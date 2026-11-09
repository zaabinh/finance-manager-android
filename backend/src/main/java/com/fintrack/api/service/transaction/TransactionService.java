package com.fintrack.api.service.transaction;

import com.fintrack.api.dto.request.transaction.TransactionRequest;
import com.fintrack.api.dto.response.transaction.TransactionPageResponse;
import com.fintrack.api.dto.response.transaction.TransactionResponse;
import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.repository.TransactionRepository;
import com.fintrack.api.repository.TransactionRepository.Fields;
import com.fintrack.api.service.finance.FinanceSupport;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Income/expense ledger writer. Lock protocol (contract §5): transaction row, then wallets in
 * {@link FinanceSupport#LOCK_ORDER}, then category; balances are read in a fresh statement after the wallet locks.
 */
@Service
@Profile("!test")
public class TransactionService {
    public static final List<String> SORTS = List.of("occurredAt,desc", "occurredAt,asc", "amountMinor,desc", "amountMinor,asc");
    private final TransactionRepository transactions;
    private final FinanceSupport finance;

    public TransactionService(TransactionRepository transactions, FinanceSupport finance) {
        this.transactions = transactions; this.finance = finance;
    }

    /** created=false means an idempotent replay (HTTP 200) of the current record. */
    public record Result(TransactionResponse body, boolean created) {}

    @Transactional(readOnly = true)
    public TransactionResponse detail(UUID user, UUID id) { return transactions.detail(user, id); }

    @Transactional(readOnly = true)
    public TransactionPageResponse list(UUID user, int page, int size, String dateFrom, String dateTo, UUID walletId,
            UUID categoryId, String type, String q, String sort) {
        if (page < 0) throw FinanceSupport.badRequest("page must be >= 0");
        if (size < 1 || size > 200) throw FinanceSupport.badRequest("size must be 1-200");
        LocalDate from = date(dateFrom, "dateFrom"), to = date(dateTo, "dateTo");
        if (from != null && to != null && from.isAfter(to)) throw FinanceSupport.badRequest("dateFrom must not be after dateTo");
        if (type != null && !List.of("EXPENSE", "INCOME").contains(type)) throw FinanceSupport.badRequest("type không hợp lệ");
        String search = q == null ? null : q.strip();
        if (search != null && (search.codePointCount(0, search.length()) > 200 || search.indexOf('\0') >= 0))
            throw FinanceSupport.badRequest("q must be at most 200 characters");
        String order = sort == null ? SORTS.get(0) : sort;
        if (!SORTS.contains(order)) throw FinanceSupport.badRequest("sort không hợp lệ");
        if (walletId != null) finance.wallet(user, walletId, false);
        if (categoryId != null) finance.category(user, categoryId, false);
        ZoneId zone = finance.zone(user);
        var result = transactions.page(user, from == null ? null : from.atStartOfDay(zone).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant(), walletId, categoryId, type,
                search == null || search.isEmpty() ? null : search, order, size, (long) page * size);
        return new TransactionPageResponse(result.items(), page, (int) ((result.total() + size - 1) / size));
    }

    @Transactional
    public Result create(UUID user, TransactionRequest request) {
        Fields fields = fields(request);
        if (request.clientRequestId == null) throw FinanceSupport.invalid("clientRequestId is required");
        String fingerprint = fingerprint(fields);
        // Serialize the user/key before any reference checks, including retries with a changed wallet.
        transactions.lockRequest(user, request.clientRequestId);
        Result replay = replay(user, request.clientRequestId, fingerprint);
        if (replay != null) return replay;
        var wallet = finance.wallet(user, fields.walletId(), true);
        if (!wallet.active()) throw FinanceSupport.conflict(ErrorCode.WALLET_ARCHIVED, "Ví đã lưu trữ");
        category(user, fields);
        ensureFunds(user, fields.walletId(), fields.delta());
        UUID id = UUID.randomUUID();
        if (!transactions.create(user, id, fields, request.clientRequestId, fingerprint))
            // key committed concurrently on another wallet; the row cannot vanish short of a user cascade delete
            return java.util.Objects.requireNonNull(replay(user, request.clientRequestId, fingerprint));
        return new Result(transactions.detail(user, id), true);
    }

    @Transactional
    public TransactionResponse update(UUID user, UUID id, TransactionRequest request) {
        Fields fields = fields(request);
        var old = transactions.lock(user, id);
        if (!old.posted()) throw FinanceSupport.conflict(ErrorCode.TRANSACTION_VOIDED, "Giao dịch đã bị xóa");
        List<UUID> wallets = Stream.of(old.walletId(), fields.walletId()).distinct().sorted(FinanceSupport.LOCK_ORDER).toList();
        for (UUID wallet : wallets) {
            var ref = finance.wallet(user, wallet, true);
            if (wallet.equals(fields.walletId()) && !ref.active())
                throw FinanceSupport.conflict(ErrorCode.WALLET_ARCHIVED, "Ví đã lưu trữ");
        }
        category(user, fields);
        for (UUID wallet : wallets) {
            long change = (wallet.equals(fields.walletId()) ? fields.delta() : 0) - (wallet.equals(old.walletId()) ? old.delta() : 0);
            ensureFunds(user, wallet, change);
        }
        transactions.update(user, id, fields);
        return transactions.detail(user, id);
    }

    @Transactional
    public void delete(UUID user, UUID id) {
        var old = transactions.lock(user, id);
        if (!old.posted()) return; // repeated void keeps the first voidedAt and reverses nothing
        finance.wallet(user, old.walletId(), true); // archived wallet is fine; funds still checked
        ensureFunds(user, old.walletId(), -old.delta());
        transactions.voidPosted(user, id);
    }

    private Result replay(UUID user, UUID clientRequestId, String fingerprint) {
        var existing = transactions.byRequest(user, clientRequestId).orElse(null);
        if (existing == null) return null;
        if (!fingerprint.equals(existing.fingerprint()))
            throw FinanceSupport.conflict(ErrorCode.TRANSACTION_REQUEST_CONFLICT, "clientRequestId đã dùng cho nội dung khác");
        return new Result(transactions.detail(user, existing.id()), false);
    }

    private void category(UUID user, Fields fields) {
        var category = finance.category(user, fields.categoryId(), true);
        if (!category.active()) throw FinanceSupport.conflict(ErrorCode.CATEGORY_ARCHIVED, "Danh mục đã lưu trữ");
        if (!fields.type().equals(category.categoryType())) throw FinanceSupport.invalid("Danh mục không cùng loại giao dịch");
    }

    /** Call only after the wallet lock; reads the balance in a new statement. */
    private void ensureFunds(UUID user, UUID wallet, long change) {
        BigInteger candidate = BigInteger.valueOf(finance.balance(user, wallet)).add(BigInteger.valueOf(change));
        if (candidate.signum() < 0) throw FinanceSupport.conflict(ErrorCode.WALLET_INSUFFICIENT_FUNDS, "Số dư ví không đủ");
        FinanceSupport.exact(new BigDecimal(candidate));
    }

    static Fields fields(TransactionRequest request) {
        if (request.transactionType == null) throw FinanceSupport.invalid("transactionType is required");
        if (!List.of("EXPENSE", "INCOME").contains(request.transactionType))
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.TRANSACTION_TYPE_UNSUPPORTED, "Chỉ hỗ trợ thu/chi");
        if (request.destinationWalletId != null) throw FinanceSupport.invalid("destinationWalletId must be null");
        long amount = FinanceSupport.money(request.amountMinor, false);
        if (request.categoryId == null) throw FinanceSupport.invalid("categoryId is required");
        if (request.walletId == null) throw FinanceSupport.invalid("walletId is required");
        return new Fields(request.transactionType, amount, request.categoryId, request.walletId,
                occurredAt(request.occurredAt), text(request.note, 2000, "note"), text(request.payee, 160, "payee"));
    }

    private static Instant occurredAt(String value) {
        if (value == null) throw FinanceSupport.invalid("occurredAt is required");
        Instant instant;
        try { instant = OffsetDateTime.parse(value).toInstant(); }
        catch (DateTimeException error) { throw FinanceSupport.badRequest("occurredAt must be an ISO-8601 timestamp with offset"); }
        int year = instant.atOffset(ZoneOffset.UTC).getYear();
        if (year < 1 || year > 9998) throw FinanceSupport.invalid("occurredAt year must be 0001-9998");
        return instant;
    }

    /** Empty and null are the same value (response normalizes both to ""). */
    private static String text(String value, int max, String field) {
        if (value == null || value.isEmpty()) return null;
        if (value.codePointCount(0, value.length()) > max || value.indexOf('\0') >= 0)
            throw FinanceSupport.invalid(field + " must be at most " + max + " characters");
        return value;
    }

    private static LocalDate date(String value, String field) {
        if (value == null) return null;
        try {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new DateTimeException(value);
            LocalDate date = LocalDate.parse(value);
            if (date.getYear() < 1 || date.getYear() > 9998) throw new DateTimeException(value);
            return date;
        } catch (DateTimeException error) { throw FinanceSupport.badRequest(field + " must be YYYY-MM-DD, year 0001-9998"); }
    }

    /** SHA-256 over length-prefixed canonical fields of the original POST; clientRequestId is the key, not part of it. */
    static String fingerprint(Fields f) {
        StringBuilder canonical = new StringBuilder("v1");
        for (String part : new String[]{f.type(), Long.toString(f.amount()), f.categoryId().toString(), f.walletId().toString(),
                f.occurredAt().toString(), f.note() == null ? "" : f.note(), f.payee() == null ? "" : f.payee()})
            canonical.append('|').append(part.length()).append(':').append(part);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
