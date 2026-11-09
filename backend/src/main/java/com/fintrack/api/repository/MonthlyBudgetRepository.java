package com.fintrack.api.repository;

import com.fintrack.api.service.finance.FinanceSupport;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Monthly budget containers. The total is always derived from category budgets, never stored. */
@Repository
@Profile("!test")
public class MonthlyBudgetRepository {
    private final JdbcTemplate jdbc;
    public MonthlyBudgetRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record MonthlyRow(UUID id, YearMonth month, boolean active) {}
    /** Aggregates of one monthly budget, computed by one statement. */
    public record Summary(UUID id, YearMonth month, long totalMinor, long spentMinor, int threshold,
            Instant archivedAt, Instant createdAt, Instant updatedAt) {}

    // Active parent: its active children. Archived parent: the children archived with it (same timestamp).
    // Spent: every POSTED EXPENSE in the user's local month, any category; TRANSFER is never an expense.
    private static final String SELECT = """
            SELECT m.*, COALESCE(n.budget_alert_threshold_percent, 80) AS threshold,
                   COALESCE((SELECT SUM(b.limit_minor) FROM fintrack.budgets b
                             WHERE b.monthly_budget_id = m.id AND b.archived_at IS NOT DISTINCT FROM m.archived_at), 0) AS total,
                   COALESCE(usage.spent, 0) AS spent
            FROM fintrack.monthly_budgets m
            LEFT JOIN fintrack.user_preferences p ON p.user_id = m.user_id
            LEFT JOIN fintrack.notification_settings n ON n.user_id = m.user_id
            LEFT JOIN LATERAL (
                SELECT SUM(t.amount_minor) AS spent FROM fintrack.transactions t
                WHERE t.user_id = m.user_id AND t.transaction_type = 'EXPENSE' AND t.status = 'POSTED'
                  AND t.occurred_at >= (m.month::timestamp AT TIME ZONE COALESCE(p.time_zone, 'Asia/Ho_Chi_Minh'))
                  AND t.occurred_at < ((m.month + INTERVAL '1 month')::timestamp AT TIME ZONE COALESCE(p.time_zone, 'Asia/Ho_Chi_Minh'))
            ) usage ON true
            WHERE m.user_id = ?
            """;

    public List<Summary> list(UUID userId, YearMonth month, String lifecycle) {
        StringBuilder sql = new StringBuilder(SELECT);
        List<Object> args = new ArrayList<>(); args.add(userId);
        if (month != null) { sql.append(" AND m.month = ?"); args.add(month.atDay(1)); }
        if (!"ALL".equals(lifecycle)) sql.append(" AND m.archived_at IS ").append("ACTIVE".equals(lifecycle) ? "NULL" : "NOT NULL");
        sql.append(" ORDER BY m.month DESC, m.id ASC");
        return jdbc.query(sql.toString(), MonthlyBudgetRepository::summary, args.toArray());
    }
    public Summary detail(UUID userId, UUID id) {
        return jdbc.query(SELECT + " AND m.id = ?", MonthlyBudgetRepository::summary, userId, id)
                .stream().findFirst().orElseThrow(FinanceSupport::notFound);
    }
    /** First lock of every write under a monthly budget (lock order: monthly budget, category budget, category). */
    public MonthlyRow lock(UUID userId, UUID id) {
        return jdbc.query("SELECT id, month, archived_at FROM fintrack.monthly_budgets WHERE user_id = ? AND id = ? FOR UPDATE",
                MonthlyBudgetRepository::row, userId, id).stream().findFirst().orElseThrow(FinanceSupport::notFound);
    }
    public Optional<MonthlyRow> lockActive(UUID userId, YearMonth month) {
        return jdbc.query("SELECT id, month, archived_at FROM fintrack.monthly_budgets WHERE user_id = ? AND month = ? AND archived_at IS NULL FOR UPDATE",
                MonthlyBudgetRepository::row, userId, month.atDay(1)).stream().findFirst();
    }
    public UUID create(UUID userId, YearMonth month) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO fintrack.monthly_budgets(id, user_id, month) VALUES (?, ?, ?)", id, userId, month.atDay(1));
        return id;
    }
    /** Archives the parent and all its active children with one timestamp, inside the caller's transaction. */
    public void archive(UUID userId, UUID id) {
        jdbc.update("UPDATE fintrack.budgets SET archived_at = now(), updated_at = now() WHERE user_id = ? AND monthly_budget_id = ? AND archived_at IS NULL", userId, id);
        jdbc.update("UPDATE fintrack.monthly_budgets SET archived_at = now(), updated_at = now(), version = version + 1 WHERE user_id = ? AND id = ? AND archived_at IS NULL", userId, id);
    }
    public void touch(UUID userId, UUID id) {
        jdbc.update("UPDATE fintrack.monthly_budgets SET updated_at = now(), version = version + 1 WHERE user_id = ? AND id = ?", userId, id);
    }
    /** Derived monthly total; call after locking the monthly budget, in a new statement. */
    public long total(UUID userId, UUID id) {
        return FinanceSupport.exact(jdbc.queryForObject("SELECT COALESCE(SUM(limit_minor), 0) FROM fintrack.budgets WHERE user_id = ? AND monthly_budget_id = ? AND archived_at IS NULL",
                java.math.BigDecimal.class, userId, id));
    }
    /** All POSTED EXPENSE of the user's local month (any category). */
    public long spent(UUID userId, YearMonth month) {
        return FinanceSupport.exact(jdbc.queryForObject("""
                SELECT COALESCE(SUM(t.amount_minor), 0) FROM fintrack.transactions t
                LEFT JOIN fintrack.user_preferences p ON p.user_id = t.user_id
                WHERE t.user_id = ? AND t.transaction_type = 'EXPENSE' AND t.status = 'POSTED'
                  AND t.occurred_at >= (?::timestamp AT TIME ZONE COALESCE(p.time_zone, 'Asia/Ho_Chi_Minh'))
                  AND t.occurred_at < (?::timestamp AT TIME ZONE COALESCE(p.time_zone, 'Asia/Ho_Chi_Minh'))
                """, java.math.BigDecimal.class, userId, month.atDay(1), month.plusMonths(1).atDay(1)));
    }
    /** Spendable money: active wallets counted in assets, savings excluded. */
    public long available(UUID userId) {
        return FinanceSupport.exact(jdbc.queryForObject("""
                SELECT COALESCE(SUM(balance_minor), 0) FROM fintrack.wallet_balances
                WHERE user_id = ? AND archived_at IS NULL AND include_in_assets AND wallet_type <> 'SAVINGS'
                """, java.math.BigDecimal.class, userId));
    }
    private static MonthlyRow row(ResultSet rs, int row) throws SQLException {
        return new MonthlyRow(rs.getObject("id", UUID.class), YearMonth.from(rs.getObject("month", LocalDate.class)),
                rs.getObject("archived_at") == null);
    }
    private static Summary summary(ResultSet rs, int row) throws SQLException {
        return new Summary(rs.getObject("id", UUID.class), YearMonth.from(rs.getObject("month", LocalDate.class)),
                FinanceSupport.exact(rs.getBigDecimal("total")), FinanceSupport.exact(rs.getBigDecimal("spent")),
                rs.getInt("threshold"), BudgetRepository.instant(rs, "archived_at"),
                BudgetRepository.instant(rs, "created_at"), BudgetRepository.instant(rs, "updated_at"));
    }
}
