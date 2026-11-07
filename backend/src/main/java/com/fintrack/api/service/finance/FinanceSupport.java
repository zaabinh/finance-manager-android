package com.fintrack.api.service.finance;

import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.ErrorCode;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Shared reference reads and row locks; writes remain in each feature's repository. */
@Component
@Profile("!test")
public class FinanceSupport {
    public static final long MAX_MONEY = 999_999_999_999L;
    /** Lock order for several wallets/categories: lowercase-hex order equals PostgreSQL uuid (unsigned byte) order. */
    public static final java.util.Comparator<UUID> LOCK_ORDER = java.util.Comparator.comparing(UUID::toString);
    private final JdbcTemplate jdbc;

    public FinanceSupport(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record WalletRef(UUID id, long openingBalanceMinor, boolean active) {}
    public record CategoryRef(UUID id, String categoryType, boolean active) {}

    public WalletRef wallet(UUID userId, UUID walletId, boolean lock) {
        return jdbc.query("SELECT id, opening_balance_minor, archived_at FROM fintrack.wallets "
                        + "WHERE user_id = ? AND id = ?" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new WalletRef(rs.getObject("id", UUID.class),
                        rs.getLong("opening_balance_minor"), rs.getObject("archived_at") == null), userId, walletId)
                .stream().findFirst().orElseThrow(FinanceSupport::notFound);
    }

    public CategoryRef category(UUID userId, UUID categoryId, boolean lock) {
        return jdbc.query("SELECT id, category_type, archived_at FROM fintrack.categories "
                        + "WHERE user_id = ? AND id = ?" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new CategoryRef(rs.getObject("id", UUID.class),
                        rs.getString("category_type"), rs.getObject("archived_at") == null), userId, categoryId)
                .stream().findFirst().orElseThrow(FinanceSupport::notFound);
    }

    /** Call in a new statement after acquiring wallet locks, never in the lock statement snapshot. */
    public long balance(UUID userId, UUID walletId) {
        return jdbc.query("SELECT balance_minor FROM fintrack.wallet_balances WHERE user_id = ? AND wallet_id = ?",
                (rs, row) -> exact(rs.getBigDecimal("balance_minor")), userId, walletId)
                .stream().findFirst().orElseThrow(FinanceSupport::notFound);
    }

    public ZoneId zone(UUID userId) {
        String zone = jdbc.query("SELECT time_zone FROM fintrack.user_preferences WHERE user_id = ?",
                (rs, row) -> rs.getString(1), userId).stream().findFirst().orElse("Asia/Ho_Chi_Minh");
        return ZoneId.of(zone);
    }

    public int warningThreshold(UUID userId) {
        return jdbc.query("SELECT budget_alert_threshold_percent FROM fintrack.notification_settings WHERE user_id = ?",
                (rs, row) -> rs.getInt(1), userId).stream().findFirst().orElse(80);
    }

    public static long exact(BigDecimal value) {
        try { return value.longValueExact(); }
        catch (ArithmeticException exception) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.FINANCE_AMOUNT_OVERFLOW,
                    "Số tiền vượt giới hạn xử lý");
        }
    }

    public static String name(String value) {
        if (value == null) throw invalid("Tên không được để trống");
        String normalized = Normalizer.normalize(value.strip(), Normalizer.Form.NFC);
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 100)
            throw invalid("Tên phải có từ 1 đến 100 ký tự");
        return normalized;
    }

    public static long money(Long value, boolean allowZero) {
        if (value == null || value < (allowZero ? 0 : 1) || value > MAX_MONEY)
            throw invalid("Số tiền VND không hợp lệ");
        return value;
    }

    public static int sort(Integer value) {
        if (value != null && value < 0) throw invalid("sortOrder không được âm");
        return value == null ? 0 : value;
    }

    public static String type(String value, String... allowed) {
        if (value == null || !Arrays.asList(allowed).contains(value)) throw invalid("Loại không hợp lệ");
        return value;
    }

    public static String lifecycle(String value) {
        String status = value == null ? "ALL" : value;
        if (!Arrays.asList("ACTIVE", "ARCHIVED", "ALL").contains(status)) throw badRequest("status không hợp lệ");
        return status;
    }

    public static AppException invalid(String message) {
        return new AppException(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.VALIDATION_ERROR, message);
    }
    public static AppException badRequest(String message) {
        return new AppException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, message);
    }
    public static AppException notFound() {
        return new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Không tìm thấy dữ liệu");
    }
    public static AppException conflict(ErrorCode code, String message) {
        return new AppException(HttpStatus.CONFLICT, code, message);
    }
}
