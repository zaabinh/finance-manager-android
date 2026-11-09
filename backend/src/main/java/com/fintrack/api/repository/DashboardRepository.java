package com.fintrack.api.repository;

import com.fintrack.api.dto.response.dashboard.DashboardResponse.*;
import com.fintrack.api.service.finance.FinanceSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
public class DashboardRepository {
    private final JdbcTemplate jdbc;
    public DashboardRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Profile(String displayName, ZoneId zone, Instant snapshotAt) {}

    public Profile profile(UUID user) {
        return jdbc.query("""
                SELECT COALESCE(NULLIF(BTRIM(p.display_name), ''), NULLIF(BTRIM(u.username::text), ''), 'bạn') AS display_name,
                       COALESCE(p.time_zone, 'Asia/Ho_Chi_Minh') AS time_zone, transaction_timestamp() AS snapshot_at
                FROM fintrack.users u LEFT JOIN fintrack.user_preferences p ON p.user_id = u.id WHERE u.id = ?
                """, (rs, row) -> new Profile(rs.getString("display_name"), ZoneId.of(rs.getString("time_zone")),
                        rs.getTimestamp("snapshot_at").toInstant()), user)
                .stream().findFirst().orElseThrow(FinanceSupport::notFound);
    }
    public Assets assets(UUID user) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(balance_minor), 0) AS total,
                       COALESCE(SUM(balance_minor) FILTER (WHERE wallet_type <> 'SAVINGS'), 0) AS available,
                       COALESCE(SUM(balance_minor) FILTER (WHERE wallet_type = 'SAVINGS'), 0) AS savings, COUNT(*) AS wallets
                FROM fintrack.wallet_balances WHERE user_id = ? AND archived_at IS NULL AND include_in_assets
                """, (rs, row) -> new Assets(FinanceSupport.exact(rs.getBigDecimal("total")),
                        FinanceSupport.exact(rs.getBigDecimal("available")), FinanceSupport.exact(rs.getBigDecimal("savings")),
                        rs.getLong("wallets")), user);
    }
    public MonthlyFlow flow(UUID user, Instant start, Instant end) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount_minor) FILTER (WHERE transaction_type = 'INCOME'), 0) AS income,
                       COALESCE(SUM(amount_minor) FILTER (WHERE transaction_type = 'EXPENSE'), 0) AS expense
                FROM fintrack.transactions WHERE user_id = ? AND status = 'POSTED'
                  AND transaction_type IN ('INCOME', 'EXPENSE') AND occurred_at >= ? AND occurred_at < ?
                """, (rs, row) -> new MonthlyFlow(FinanceSupport.exact(rs.getBigDecimal("income")),
                        FinanceSupport.exact(rs.getBigDecimal("expense")),
                        FinanceSupport.exact(rs.getBigDecimal("income").subtract(rs.getBigDecimal("expense")))),
                user, Timestamp.from(start), Timestamp.from(end));
    }
    public List<RecentTransaction> recent(UUID user, Instant start, Instant end) {
        return jdbc.query("""
                SELECT t.id, t.transaction_type, t.amount_minor, t.occurred_at,
                       w.id AS wallet_id, w.name AS wallet_name, w.archived_at AS wallet_archived,
                       c.id AS category_id, c.name AS category_name, c.icon_key, c.color_hex, c.archived_at AS category_archived
                FROM (SELECT * FROM fintrack.transactions WHERE user_id = ? AND status = 'POSTED'
                      AND transaction_type IN ('INCOME', 'EXPENSE') AND occurred_at >= ? AND occurred_at < ?
                      ORDER BY occurred_at DESC, id DESC LIMIT 5) t
                JOIN fintrack.transaction_entries e ON e.user_id = t.user_id AND e.transaction_id = t.id
                JOIN fintrack.wallets w ON w.user_id = t.user_id AND w.id = e.wallet_id
                JOIN fintrack.categories c ON c.user_id = t.user_id AND c.id = t.category_id
                ORDER BY t.occurred_at DESC, t.id DESC
                """, (rs, row) -> new RecentTransaction(rs.getObject("id", UUID.class), rs.getString("transaction_type"),
                        rs.getLong("amount_minor"), rs.getTimestamp("occurred_at").toInstant(),
                        new Wallet(rs.getObject("wallet_id", UUID.class), rs.getString("wallet_name"), rs.getObject("wallet_archived") != null),
                        new Category(rs.getObject("category_id", UUID.class), rs.getString("category_name"), rs.getString("icon_key"),
                                rs.getString("color_hex"), rs.getObject("category_archived") != null)),
                user, Timestamp.from(start), Timestamp.from(end));
    }
}
