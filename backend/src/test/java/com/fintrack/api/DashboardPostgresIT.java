package com.fintrack.api;

import static org.assertj.core.api.Assertions.*;
import com.fintrack.api.dto.response.dashboard.DashboardResponse;
import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.repository.BudgetRepository;
import com.fintrack.api.repository.DashboardRepository;
import com.fintrack.api.service.dashboard.DashboardService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** Isolated user fixtures; never truncates or cleans an externally supplied test database. */
class DashboardPostgresIT {
    static PostgreSQLContainer<?> postgres;
    static JdbcTemplate jdbc;
    static JdbcTransactionManager manager;
    static TransactionTemplate writes;
    static BudgetRepository budgets;
    static DashboardRepository repository;
    static DashboardService service;
    UUID user, cash, savings, excluded, archived, expense, income;

    @BeforeAll static void start() {
        String url = System.getenv("FINANCE_TEST_JDBC_URL");
        DriverManagerDataSource dataSource;
        if (url != null && !url.isBlank()) dataSource = new DriverManagerDataSource(url,
                System.getenv("FINANCE_TEST_DB_USER"), System.getenv("FINANCE_TEST_DB_PASSWORD"));
        else {
            postgres = new PostgreSQLContainer<>("postgres:17.7"); postgres.start();
            dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        }
        Flyway.configure().dataSource(dataSource).defaultSchema("fintrack").load().migrate();
        jdbc = new JdbcTemplate(dataSource); manager = new JdbcTransactionManager(dataSource); writes = new TransactionTemplate(manager);
        repository = new DashboardRepository(jdbc); budgets = new BudgetRepository(jdbc); service = proxied(repository);
    }
    static DashboardService proxied(DashboardRepository repository) {
        var factory = new ProxyFactory(new DashboardService(repository, budgets));
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (DashboardService) factory.getProxy();
    }
    @AfterAll static void stop() { if (postgres != null) postgres.stop(); }
    @BeforeEach void fixtures() {
        user = owner();
        jdbc.update("INSERT INTO fintrack.user_preferences(user_id,display_name,time_zone) VALUES (?,'Dashboard An','Asia/Ho_Chi_Minh')", user);
        jdbc.update("INSERT INTO fintrack.notification_settings(user_id,notifications_enabled,budget_alert_threshold_percent) VALUES (?,false,80)", user);
        cash = wallet(user, "CASH", 1_000_000, true, false);
        savings = wallet(user, "SAVINGS", 2_000_000, true, false);
        excluded = wallet(user, "BANK", 3_000_000, false, false);
        archived = wallet(user, "CASH", 4_000_000, true, true);
        expense = category(user, "EXPENSE"); income = category(user, "INCOME");
    }
    @Test void assetsAreCurrentWhileFlowsIncludeArchivedExcludedWalletsAndExcludeTransfersAndVoids() {
        post(user, cash, income, "INCOME", 200, "2026-11-05T00:00:00Z", false);
        post(user, excluded, expense, "EXPENSE", 400, "2026-11-05T00:00:00Z", false);
        post(user, archived, expense, "EXPENSE", 300, "2026-11-05T00:00:00Z", false);
        post(user, cash, expense, "EXPENSE", 900, "2026-11-05T00:00:00Z", true);
        transfer(cash, savings, 50);
        var result = service.get(user, "2026-11");
        assertThat(result.assets().totalMinor()).isEqualTo(3_000_200);
        assertThat(result.assets().availableMinor()).isEqualTo(1_000_150);
        assertThat(result.assets().savingsMinor()).isEqualTo(2_000_050);
        assertThat(result.assets().walletCount()).isEqualTo(2);
        assertThat(result.monthlyFlow().incomeMinor()).isEqualTo(200);
        assertThat(result.monthlyFlow().expenseMinor()).isEqualTo(700);
        assertThat(result.monthlyFlow().netSavingsMinor()).isEqualTo(-500);
        assertThat(result.recentTransactions()).hasSize(3);
        assertThat(service.get(user, "2024-02").assets()).isEqualTo(result.assets());
        assertThat(service.get(user, "9998-12").monthlyFlow().incomeMinor()).isZero();
    }
    @Test void selectedMonthHasHalfOpenTimezoneBoundariesAndStableRecentFiveOrdering() {
        post(user, cash, expense, "EXPENSE", 1, "2026-10-31T16:59:59Z", false);
        post(user, cash, expense, "EXPENSE", 2, "2026-10-31T17:00:00Z", false);
        for (int i = 0; i < 7; i++) post(user, cash, expense, "EXPENSE", 3, "2026-11-30T16:59:59Z", false);
        post(user, cash, expense, "EXPENSE", 4, "2026-11-30T17:00:00Z", false);
        var result = service.get(user, "2026-11");
        assertThat(result.monthlyFlow().expenseMinor()).isEqualTo(23);
        assertThat(result.recentTransactions()).hasSize(5);
        var expected = jdbc.query("SELECT id FROM fintrack.transactions WHERE user_id=? AND occurred_at=? ORDER BY id DESC LIMIT 5",
                (rs, row) -> rs.getObject(1, UUID.class), user, Timestamp.from(Instant.parse("2026-11-30T16:59:59Z")));
        assertThat(result.recentTransactions()).extracting(DashboardResponse.RecentTransaction::transactionId).containsExactlyElementsOf(expected);
    }
    @Test void leapDecemberAndDstUseUserCalendar() {
        jdbc.update("UPDATE fintrack.user_preferences SET time_zone='America/New_York' WHERE user_id=?", user);
        post(user, cash, expense, "EXPENSE", 1, "2026-03-01T04:59:59Z", false);
        post(user, cash, expense, "EXPENSE", 2, "2026-03-01T05:00:00Z", false);
        post(user, cash, expense, "EXPENSE", 3, "2026-04-01T03:59:59Z", false);
        post(user, cash, expense, "EXPENSE", 4, "2026-04-01T04:00:00Z", false);
        assertThat(service.get(user, "2026-03").monthlyFlow().expenseMinor()).isEqualTo(5);
        jdbc.update("UPDATE fintrack.user_preferences SET time_zone='UTC' WHERE user_id=?", user);
        assertThat(service.get(user, "2026-03").monthlyFlow().expenseMinor()).isEqualTo(3);
        post(user, cash, expense, "EXPENSE", 7, "2024-02-29T23:59:59Z", false);
        post(user, cash, expense, "EXPENSE", 8, "2024-03-01T00:00:00Z", false);
        assertThat(service.get(user, "2024-02").monthlyFlow().expenseMinor()).isEqualTo(7);
        post(user, cash, expense, "EXPENSE", 9, "2026-12-31T23:59:59Z", false);
        post(user, cash, expense, "EXPENSE", 10, "2027-01-01T00:00:00Z", false);
        assertThat(service.get(user, "2026-12").monthlyFlow().expenseMinor()).isEqualTo(9);
    }
    @Test void budgetRiskMatchesCrudRetainsArchivedCategoryAndExcludesArchivedParentOrChild() {
        UUID second = category(user, "EXPENSE"), parent = parent("2026-11");
        UUID first = budget(parent, expense, "2026-11", 100);
        UUID other = budget(parent, second, "2026-11", 900);
        post(user, archived, expense, "EXPENSE", 100, "2026-11-03T00:00:00Z", false);
        jdbc.update("UPDATE fintrack.categories SET archived_at=now() WHERE id=?", expense);
        var result = service.get(user, "2026-11");
        assertThat(result.budget().count()).isEqualTo(2);
        assertThat(result.budget().worstStatus()).isEqualTo(budgets.detail(user, first).status()).isEqualTo("EXCEEDED");
        assertThat(result.budget().usagePercent()).isEqualByComparingTo("10.00");
        assertThat(result.recentTransactions().getFirst().wallet().archived()).isTrue();
        assertThat(result.recentTransactions().getFirst().category().archived()).isTrue();
        jdbc.update("UPDATE fintrack.budgets SET archived_at=now() WHERE id=?", other);
        assertThat(service.get(user, "2026-11").budget().count()).isEqualTo(1);
        jdbc.update("UPDATE fintrack.monthly_budgets SET archived_at=now() WHERE id=?", parent);
        assertThat(service.get(user, "2026-11").budget().worstStatus()).isEqualTo("NONE");
    }
    @Test void thresholdSettingsChangeStatusButToggleDoesNotHideRisk() {
        UUID parent = parent("2026-11"); budget(parent, expense, "2026-11", 100);
        post(user, cash, expense, "EXPENSE", 80, "2026-11-03T00:00:00Z", false);
        assertThat(service.get(user, "2026-11").budget().worstStatus()).isEqualTo("WARNING");
        jdbc.update("UPDATE fintrack.notification_settings SET budget_alert_threshold_percent=100 WHERE user_id=?", user);
        assertThat(service.get(user, "2026-11").budget().worstStatus()).isEqualTo("SAFE");
        post(user, cash, expense, "EXPENSE", 20, "2026-11-03T00:00:00Z", false);
        assertThat(service.get(user, "2026-11").budget().worstStatus()).isEqualTo("EXCEEDED");
    }
    @Test void emptyOtherOwnerCannotSeeAnyProjectionAndDefaultsHaveRealZeroValues() {
        UUID parent = parent("2026-11"); budget(parent, expense, "2026-11", 100);
        post(user, cash, expense, "EXPENSE", 10, "2026-11-03T00:00:00Z", false);
        UUID other = owner(); var result = service.get(other, "2026-11");
        assertThat(result.assets().totalMinor()).isZero();
        assertThat(result.monthlyFlow().expenseMinor()).isZero();
        assertThat(result.budget().count()).isZero();
        assertThat(result.budget().worstStatus()).isEqualTo("NONE");
        assertThat(result.recentTransactions()).isEmpty();
        assertThat(result.user().displayName()).isEqualTo("dashboard-" + other);
        assertThat(result.timeZone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(result.notifications().unreadCount()).isNull();
        assertThat(service.get(other, null).month()).isEqualTo(YearMonth.from(result.snapshotAt().atZone(java.time.ZoneId.of(result.timeZone()))).toString());
        parent("2026-12"); assertThat(service.get(user, "2026-12").budget().worstStatus()).isEqualTo("NONE");
    }
    @Test void snapshotDoesNotMixConcurrentExpenseVoidRenameAndTimezoneChanges() throws Exception {
        UUID transaction = post(user, cash, expense, "EXPENSE", 100, "2026-11-03T00:00:00Z", false);
        UUID parent = parent("2026-11"); budget(parent, expense, "2026-11", 200);
        var firstRead = new CountDownLatch(1); var committed = new CountDownLatch(1); var once = new AtomicBoolean();
        var blocking = new DashboardRepository(jdbc) {
            @Override public Profile profile(UUID owner) {
                var profile = super.profile(owner);
                assertThat(jdbc.queryForObject("SHOW transaction_isolation", String.class)).isEqualTo("repeatable read");
                assertThat(jdbc.queryForObject("SHOW transaction_read_only", String.class)).isEqualTo("on");
                if (once.compareAndSet(false, true)) {
                    firstRead.countDown();
                    try { assertThat(committed.await(15, TimeUnit.SECONDS)).isTrue(); }
                    catch (InterruptedException error) { throw new AssertionError(error); }
                }
                return profile;
            }
        };
        var isolated = proxied(blocking);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var pending = pool.submit(() -> isolated.get(user, "2026-11"));
            assertThat(firstRead.await(15, TimeUnit.SECONDS)).isTrue();
            try {
                writes.executeWithoutResult(status -> {
                    jdbc.update("UPDATE fintrack.transactions SET status='VOIDED',voided_at=now() WHERE id=?", transaction);
                    post(user, cash, expense, "EXPENSE", 300, "2026-11-04T00:00:00Z", false);
                    jdbc.update("UPDATE fintrack.wallets SET name='Changed wallet' WHERE id=?", cash);
                    jdbc.update("UPDATE fintrack.categories SET name='Changed category' WHERE id=?", expense);
                    jdbc.update("UPDATE fintrack.user_preferences SET display_name='Changed user',time_zone='UTC' WHERE user_id=?", user);
                    jdbc.update("UPDATE fintrack.notification_settings SET budget_alert_threshold_percent=40 WHERE user_id=?", user);
                });
            } finally { committed.countDown(); }
            var before = pending.get(15, TimeUnit.SECONDS);
            assertThat(before.timeZone()).isEqualTo("Asia/Ho_Chi_Minh");
            assertThat(before.user().displayName()).isEqualTo("Dashboard An");
            assertThat(before.assets().totalMinor()).isEqualTo(2_999_900);
            assertThat(before.monthlyFlow().expenseMinor()).isEqualTo(100);
            assertThat(before.budget().worstStatus()).isEqualTo("SAFE");
            assertThat(before.recentTransactions()).extracting(DashboardResponse.RecentTransaction::transactionId).containsExactly(transaction);
            assertThat(before.recentTransactions().getFirst().wallet().name()).startsWith("Wallet-");
            var after = service.get(user, "2026-11");
            assertThat(after.assets().totalMinor()).isEqualTo(2_999_700);
            assertThat(after.monthlyFlow().expenseMinor()).isEqualTo(300);
            assertThat(after.budget().worstStatus()).isEqualTo("EXCEEDED");
            assertThat(after.recentTransactions().getFirst().category().name()).isEqualTo("Changed category");
            assertThat(after.timeZone()).isEqualTo("UTC");
        }
    }
    @Test void longBoundaryAndOverflowAreExactForAssetsFlowsAndBudgetTotals() {
        UUID extreme = owner(), w = wallet(extreme, "CASH", Long.MAX_VALUE, true, false);
        assertThat(service.get(extreme, "2026-11").assets().totalMinor()).isEqualTo(Long.MAX_VALUE);
        wallet(extreme, "SAVINGS", 1, true, false);
        assertOverflow(() -> service.get(extreme, "2026-11"));
        jdbc.update("UPDATE fintrack.wallets SET include_in_assets=false WHERE user_id=?", extreme);
        UUID category = category(extreme, "INCOME");
        post(extreme, w, category, "INCOME", Long.MAX_VALUE, "2026-11-01T00:00:00Z", false);
        assertThat(service.get(extreme, "2026-11").monthlyFlow().incomeMinor()).isEqualTo(Long.MAX_VALUE);
        post(extreme, w, category, "INCOME", 1, "2026-11-01T00:00:00Z", false);
        assertOverflow(() -> service.get(extreme, "2026-11"));
        UUID parent = parent("2026-11"); budget(parent, expense, "2026-11", Long.MAX_VALUE);
        budget(parent, category(user, "EXPENSE"), "2026-11", 1);
        assertOverflow(() -> service.get(user, "2026-11"));
    }
    @Test void largerLedgerIsAggregatedWithoutLoadingHistoryPages() {
        UUID parent = parent("2026-11"); budget(parent, expense, "2026-11", 10_000);
        writes.executeWithoutResult(status -> jdbc.update("""
                WITH inserted AS (
                    INSERT INTO fintrack.transactions(user_id,category_id,transaction_type,amount_minor,occurred_at)
                    SELECT ?,?,'EXPENSE',1,'2026-11-10T00:00:00Z'::timestamptz + i * interval '1 second'
                    FROM generate_series(1,5000) i RETURNING id,user_id
                ) INSERT INTO fintrack.transaction_entries(user_id,transaction_id,wallet_id,delta_minor)
                  SELECT user_id,id,?,-1 FROM inserted
                """, user, expense, cash));
        for (int i = 0; i < 5; i++) service.get(user, "2026-11");
        long[] samples = new long[30];
        for (int i = 0; i < samples.length; i++) {
            long started = System.nanoTime(); var result = service.get(user, "2026-11"); samples[i] = System.nanoTime() - started;
            assertThat(result.monthlyFlow().expenseMinor()).isEqualTo(5_000);
            assertThat(result.budget().spentMinor()).isEqualTo(5_000);
            assertThat(result.recentTransactions()).hasSize(5);
        }
        java.util.Arrays.sort(samples);
        System.out.printf("DASHBOARD_PERF records=5000 requests=30 concurrency=1 transport=local-JDBC p95_ms=%.2f%n", samples[28] / 1_000_000.0);
        var plan = jdbc.queryForList("""
                EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
                SELECT id FROM fintrack.transactions WHERE user_id=? AND status='POSTED'
                  AND transaction_type IN ('INCOME','EXPENSE') AND occurred_at>='2026-10-31T17:00:00Z'
                  AND occurred_at<'2026-11-30T17:00:00Z' ORDER BY occurred_at DESC,id DESC LIMIT 5
                """, String.class, user);
        System.out.println("DASHBOARD_RECENT_EXPLAIN " + String.join(" | ", plan));
    }
    private void assertOverflow(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(AppException.class,
                error -> { assertThat(error.getStatus().value()).isEqualTo(422); assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FINANCE_AMOUNT_OVERFLOW); });
    }
    private UUID owner() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO fintrack.users(id,username,email,password_hash) VALUES (?,?,?,'test-only')", id, "dashboard-" + id, id + "@dashboard.test"); return id;
    }
    private UUID wallet(UUID owner, String type, long opening, boolean included, boolean archived) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO fintrack.wallets(id,user_id,name,wallet_type,opening_balance_minor,include_in_assets,archived_at) VALUES (?,?,?,?,?,?,?)",
                id, owner, "Wallet-" + id, type, opening, included, archived ? Timestamp.from(Instant.now()) : null); return id;
    }
    private UUID category(UUID owner, String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO fintrack.categories(id,user_id,name,category_type) VALUES (?,?,?,?)", id, owner, "Category-" + id, type); return id;
    }
    private UUID parent(String month) {
        UUID id = UUID.randomUUID(); jdbc.update("INSERT INTO fintrack.monthly_budgets(id,user_id,month) VALUES (?,?,?)", id, user, YearMonth.parse(month).atDay(1)); return id;
    }
    private UUID budget(UUID parent, UUID category, String month, long limit) {
        return budgets.create(user, parent, category, YearMonth.parse(month), limit);
    }
    private UUID post(UUID owner, UUID wallet, UUID category, String type, long amount, String at, boolean voided) {
        UUID id = UUID.randomUUID();
        writes.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO fintrack.transactions(id,user_id,category_id,transaction_type,amount_minor,occurred_at,status,voided_at) VALUES (?,?,?,?,?,?,?,?)",
                    id, owner, category, type, amount, Timestamp.from(Instant.parse(at)), voided ? "VOIDED" : "POSTED", voided ? Timestamp.from(Instant.now()) : null);
            jdbc.update("INSERT INTO fintrack.transaction_entries(user_id,transaction_id,wallet_id,delta_minor) VALUES (?,?,?,?)", owner, id, wallet, "EXPENSE".equals(type) ? -amount : amount);
        }); return id;
    }
    private void transfer(UUID from, UUID to, long amount) {
        writes.executeWithoutResult(status -> {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO fintrack.transactions(id,user_id,transaction_type,amount_minor,occurred_at) VALUES (?,?,'TRANSFER',?,'2026-11-10T00:00:00Z')", id, user, amount);
            jdbc.update("INSERT INTO fintrack.transaction_entries(user_id,transaction_id,wallet_id,delta_minor) VALUES (?,?,?,?),(?,?,?,?)", user, id, from, -amount, user, id, to, amount);
        });
    }
}
