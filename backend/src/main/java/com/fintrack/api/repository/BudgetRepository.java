package com.fintrack.api.repository;

import com.fintrack.api.dto.response.budget.BudgetResponse;
import com.fintrack.api.service.finance.FinanceSupport;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
public class BudgetRepository {
    private final JdbcTemplate jdbc;
    public BudgetRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record BudgetRow(UUID id, UUID categoryId, boolean active, UUID monthlyBudgetId, YearMonth month, long limitMinor) {
        public BudgetRow(UUID id, UUID categoryId, boolean active) { this(id, categoryId, active, null, null, 0); }
    }

    // One statement snapshots budget, preferences, threshold and all matching posted expenses.
    private static final String SELECT = """
            SELECT b.*, COALESCE(n.budget_alert_threshold_percent, 80) AS threshold,
                   COALESCE(usage.spent, 0) AS spent
            FROM fintrack.budgets b
            LEFT JOIN fintrack.user_preferences p ON p.user_id = b.user_id
            LEFT JOIN fintrack.notification_settings n ON n.user_id = b.user_id
            LEFT JOIN LATERAL (
                SELECT SUM(t.amount_minor) AS spent FROM fintrack.transactions t
                WHERE t.user_id = b.user_id AND t.category_id = b.category_id
                  AND t.transaction_type = 'EXPENSE' AND t.status = 'POSTED'
                  AND t.occurred_at >= (b.period_start::timestamp AT TIME ZONE COALESCE(p.time_zone, 'Asia/Ho_Chi_Minh'))
                  AND t.occurred_at < (b.period_end::timestamp AT TIME ZONE COALESCE(p.time_zone, 'Asia/Ho_Chi_Minh'))
            ) usage ON true
            WHERE b.user_id = ?
            """;

    public List<BudgetResponse> list(UUID userId, YearMonth month, UUID categoryId, String lifecycle) {
        StringBuilder sql = new StringBuilder(SELECT);
        List<Object> args = new ArrayList<>(); args.add(userId);
        if (month != null) { sql.append(" AND b.period_start = ?"); args.add(month.atDay(1)); }
        if (categoryId != null) { sql.append(" AND b.category_id = ?"); args.add(categoryId); }
        if (!"ALL".equals(lifecycle)) sql.append(" AND b.archived_at IS ").append("ACTIVE".equals(lifecycle) ? "NULL" : "NOT NULL");
        sql.append(" ORDER BY b.period_start DESC, b.category_id ASC, b.id ASC");
        return jdbc.query(sql.toString(), BudgetRepository::response, args.toArray());
    }
    /**
     * Category budgets that belong to the given monthly budgets in their current lifecycle: active children of an
     * active parent, or the children archived together with an archived parent (same archive timestamp).
     */
    public List<BudgetResponse> byMonthly(UUID userId, Collection<UUID> monthlyBudgetIds) {
        if (monthlyBudgetIds.isEmpty()) return Collections.emptyList();
        StringBuilder sql = new StringBuilder(SELECT).append(" AND b.monthly_budget_id IN (");
        List<Object> args = new ArrayList<>(); args.add(userId);
        for (UUID id : monthlyBudgetIds) { sql.append(args.size() > 1 ? ", ?" : "?"); args.add(id); }
        sql.append(") AND b.archived_at IS NOT DISTINCT FROM (SELECT m.archived_at FROM fintrack.monthly_budgets m WHERE m.id = b.monthly_budget_id)")
                .append(" ORDER BY b.category_id ASC, b.id ASC");
        return jdbc.query(sql.toString(), BudgetRepository::response, args.toArray());
    }
    public BudgetResponse detail(UUID userId, UUID id) {
        return jdbc.query(SELECT + " AND b.id = ?", BudgetRepository::response, userId, id)
                .stream().findFirst().orElseThrow(FinanceSupport::notFound);
    }
    /** Unlocked read, used to find the parent month before locking monthly budget then category budget. */
    public BudgetRow find(UUID userId, UUID id) { return row(userId, id, false); }
    public BudgetRow lock(UUID userId, UUID id) { return row(userId, id, true); }
    private BudgetRow row(UUID userId, UUID id, boolean lock) {
        return jdbc.query("SELECT id, category_id, archived_at, monthly_budget_id, period_start, limit_minor FROM fintrack.budgets WHERE user_id = ? AND id = ?"
                        + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new BudgetRow(rs.getObject("id", UUID.class), rs.getObject("category_id", UUID.class),
                        rs.getObject("archived_at") == null, rs.getObject("monthly_budget_id", UUID.class),
                        YearMonth.from(rs.getObject("period_start", LocalDate.class)), rs.getLong("limit_minor")), userId, id)
                .stream().findFirst().orElseThrow(FinanceSupport::notFound);
    }
    public UUID create(UUID userId, UUID monthlyBudgetId, UUID categoryId, YearMonth month, long limit) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO fintrack.budgets(id, user_id, monthly_budget_id, category_id, period_start, period_end, limit_minor) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, userId, monthlyBudgetId, categoryId, month.atDay(1), month.plusMonths(1).atDay(1), limit);
        return id;
    }
    public void update(UUID userId, UUID id, UUID monthlyBudgetId, UUID categoryId, YearMonth month, long limit) {
        jdbc.update("UPDATE fintrack.budgets SET monthly_budget_id = ?, category_id = ?, period_start = ?, period_end = ?, limit_minor = ?, updated_at = now() WHERE user_id = ? AND id = ?",
                monthlyBudgetId, categoryId, month.atDay(1), month.plusMonths(1).atDay(1), limit, userId, id);
    }
    public void archive(UUID userId, UUID id) {
        jdbc.update("UPDATE fintrack.budgets SET archived_at = now(), updated_at = now() WHERE user_id = ? AND id = ? AND archived_at IS NULL", userId, id);
    }
    private static BudgetResponse response(ResultSet rs, int row) throws SQLException {
        LocalDate start = rs.getObject("period_start", LocalDate.class);
        return BudgetResponse.of(rs.getObject("id", UUID.class), rs.getObject("monthly_budget_id", UUID.class),
                rs.getObject("category_id", UUID.class),
                YearMonth.from(start).toString(), rs.getLong("limit_minor"), FinanceSupport.exact(rs.getBigDecimal("spent")),
                rs.getInt("threshold"), instant(rs, "archived_at"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }
    static Instant instant(ResultSet rs, String name) throws SQLException {
        var value = rs.getTimestamp(name); return value == null ? null : value.toInstant();
    }
}
