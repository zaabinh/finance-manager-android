package com.fintrack.api;

import static org.assertj.core.api.Assertions.*;
import com.fintrack.api.dto.request.budget.BudgetRequest;
import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.repository.BudgetRepository;
import com.fintrack.api.service.budget.BudgetService;
import com.fintrack.api.service.finance.FinanceSupport;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class BudgetPostgresIT {
    static PostgreSQLContainer<?> postgres;
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;
    static BudgetService service;
    UUID user, otherUser, category, incomeCategory, wallet;

    @BeforeAll static void start() {
        String url = System.getenv("FINANCE_TEST_JDBC_URL");
        DriverManagerDataSource dataSource;
        if (url != null && !url.isBlank()) {
            dataSource = new DriverManagerDataSource(url, System.getenv("FINANCE_TEST_DB_USER"), System.getenv("FINANCE_TEST_DB_PASSWORD"));
        } else {
            postgres = new PostgreSQLContainer<>("postgres:17.7"); postgres.start();
            dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        }
        Flyway.configure().dataSource(dataSource).defaultSchema("fintrack").load().migrate();
        jdbc = new JdbcTemplate(dataSource); tx = new TransactionTemplate(new JdbcTransactionManager(dataSource));
        service = new BudgetService(new BudgetRepository(jdbc), new FinanceSupport(jdbc));
    }
    @AfterAll static void stop() { if (postgres != null) postgres.stop(); }
    @BeforeEach void fixture() {
        user = user(); otherUser = user(); category = category(user, "EXPENSE"); incomeCategory = category(user, "INCOME"); wallet = UUID.randomUUID();
        jdbc.update("INSERT INTO fintrack.user_preferences(user_id,time_zone) VALUES (?, 'Asia/Ho_Chi_Minh')", user);
        jdbc.update("INSERT INTO fintrack.notification_settings(user_id,notifications_enabled,budget_alert_threshold_percent) VALUES (?, false, 80)", user);
        jdbc.update("INSERT INTO fintrack.wallets(id,user_id,name,wallet_type,opening_balance_minor,include_in_assets) VALUES (?, ?, 'Budget fixture', 'CASH', 10000000, false)", wallet, user);
    }
    @Test void usageIncludesArchivedExcludedWalletAndCategoryButExcludesVoidsIncomeAndOtherOwners() {
        var budget = tx.execute(status -> service.create(user, request(category, "2026-11", 100_000)));
        expense(category, "2026-10-31T16:59:59Z", 5_000, false);
        expense(category, "2026-10-31T17:00:00Z", 30_000, false);
        expense(category, "2026-11-30T16:59:59Z", 50_000, false);
        expense(category, "2026-11-30T17:00:00Z", 7_000, false);
        expense(category, "2026-11-10T17:00:00Z", 10_000, true);
        posting("INCOME", incomeCategory, "2026-11-10T17:00:00Z", 200_000, false);
        jdbc.update("UPDATE fintrack.wallets SET archived_at = now() WHERE id = ?", wallet);
        jdbc.update("UPDATE fintrack.categories SET archived_at = now() WHERE id = ?", category);
        var response = service.detail(user, budget.budgetId());
        assertThat(response.spentMinor()).isEqualTo(80_000);
        assertThat(response.status()).isEqualTo("WARNING");
        assertThat(response.remainingMinor()).isEqualTo(20_000);
        jdbc.update("UPDATE fintrack.notification_settings SET budget_alert_threshold_percent = 90 WHERE user_id = ?", user);
        assertThat(service.detail(user, budget.budgetId()).status()).isEqualTo("SAFE");
        assertThatThrownBy(() -> service.detail(otherUser, budget.budgetId())).isInstanceOfSatisfying(AppException.class,
                error -> assertThat(error.getStatus().value()).isEqualTo(404));
        assertThatThrownBy(() -> service.list(otherUser, null, category, null)).isInstanceOf(AppException.class);
        var edited = tx.execute(status -> service.update(user, budget.budgetId(), request(category, "2026-11", 40_000)));
        assertThat(edited.remainingMinor()).isEqualTo(-40_000);
    }
    @Test void timezoneChangeAndDstUseCalendarBoundaries() {
        jdbc.update("UPDATE fintrack.user_preferences SET time_zone = 'America/New_York' WHERE user_id = ?", user);
        var budget = tx.execute(status -> service.create(user, request(category, "2026-03", 100_000)));
        expense(category, "2026-03-01T04:59:59Z", 1_000, false);
        expense(category, "2026-03-01T05:00:00Z", 2_000, false);
        expense(category, "2026-04-01T03:59:59Z", 3_000, false);
        expense(category, "2026-04-01T04:00:00Z", 4_000, false);
        assertThat(service.detail(user, budget.budgetId()).spentMinor()).isEqualTo(5_000);
        jdbc.update("UPDATE fintrack.user_preferences SET time_zone = 'UTC' WHERE user_id = ?", user);
        assertThat(service.detail(user, budget.budgetId()).spentMinor()).isEqualTo(3_000);
    }
    @Test void archiveIsRepeatableDoesNotReverseBalanceAndReleasesThePeriod() {
        expense(category, "2026-11-10T00:00:00Z", 20_000, false);
        var budget = tx.execute(status -> service.create(user, request(category, "2026-11", 10_000)));
        var balance = jdbc.queryForObject("SELECT balance_minor FROM fintrack.wallet_balances WHERE wallet_id = ?", Long.class, wallet);
        tx.executeWithoutResult(status -> service.delete(user, budget.budgetId()));
        var archivedAt = service.detail(user, budget.budgetId()).archivedAt();
        tx.executeWithoutResult(status -> service.delete(user, budget.budgetId()));
        assertThat(service.detail(user, budget.budgetId()).archivedAt()).isEqualTo(archivedAt);
        assertThat(service.list(user, null, null, null)).isEmpty();
        assertThat(service.list(user, null, null, "ARCHIVED")).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT balance_minor FROM fintrack.wallet_balances WHERE wallet_id = ?", Long.class, wallet)).isEqualTo(balance);
        var replacement = tx.execute(status -> service.create(user, request(category, "2026-11", 50_000)));
        assertThat(replacement.spentMinor()).isEqualTo(20_000);
        assertThatThrownBy(() -> tx.execute(status -> service.update(user, budget.budgetId(), request(category, "2026-11", 100_000))))
                .isInstanceOfSatisfying(AppException.class, error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.BUDGET_ARCHIVED));
    }
    @Test void concurrentCreateOnlyAllowsOneActiveBudget() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var task = (java.util.concurrent.Callable<String>) () -> {
                start.await(10, TimeUnit.SECONDS);
                try { tx.execute(status -> service.create(user, request(category, "2026-11", 100_000))); return "CREATED"; }
                catch (AppException error) { return error.getErrorCode().name(); }
            };
            var first = pool.submit(task); var second = pool.submit(task); start.countDown();
            assertThat(java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("CREATED", "BUDGET_PERIOD_CONFLICT");
        }
        assertThat(service.list(user, "2026-11", category, "ACTIVE")).hasSize(1);
    }
    private UUID user() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO fintrack.users(id,username,email,password_hash) VALUES (?,?,?,'test-only')", id, "budget-" + id, id + "@budget.test"); return id;
    }
    private UUID category(UUID owner, String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO fintrack.categories(id,user_id,name,category_type) VALUES (?,?,?,?)", id, owner, "Budget-" + id, type); return id;
    }
    private void expense(UUID categoryId, String at, long amount, boolean voided) { posting("EXPENSE", categoryId, at, amount, voided); }
    private void posting(String type, UUID categoryId, String at, long amount, boolean voided) {
        UUID id = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO fintrack.transactions(id,user_id,transaction_type,category_id,amount_minor,occurred_at,status,voided_at) VALUES (?,?,?,?,?,?,?,?)",
                    id, user, type, categoryId, amount, java.sql.Timestamp.from(Instant.parse(at)), voided ? "VOIDED" : "POSTED", voided ? java.sql.Timestamp.from(Instant.now()) : null);
            jdbc.update("INSERT INTO fintrack.transaction_entries(user_id,transaction_id,wallet_id,delta_minor) VALUES (?,?,?,?)", user, id, wallet, "EXPENSE".equals(type) ? -amount : amount);
        });
    }
    private BudgetRequest request(UUID categoryId, String month, long amount) {
        BudgetRequest request = new BudgetRequest(); request.categoryId = categoryId; request.month = month; request.limitMinor = amount; return request;
    }
}
