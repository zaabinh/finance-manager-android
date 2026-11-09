package com.fintrack.api;

import static org.assertj.core.api.Assertions.*;

import com.fintrack.api.dto.request.transaction.TransactionRequest;
import com.fintrack.api.dto.response.transaction.TransactionResponse;
import com.fintrack.api.exception.AppException;
import com.fintrack.api.repository.TransactionRepository;
import com.fintrack.api.service.finance.FinanceSupport;
import com.fintrack.api.service.transaction.TransactionService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

class TransactionPostgresIT {
    static PostgreSQLContainer<?> postgres;
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;
    static TransactionService service;
    UUID user, otherUser, expenseCategory, incomeCategory, wallet;

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
        service = new TransactionService(new TransactionRepository(jdbc), new FinanceSupport(jdbc));
    }
    @AfterAll static void stop() { if (postgres != null) postgres.stop(); }
    @BeforeEach void fixture() {
        user = user(); otherUser = user();
        expenseCategory = category(user, "EXPENSE"); incomeCategory = category(user, "INCOME");
        jdbc.update("INSERT INTO fintrack.user_preferences(user_id,time_zone) VALUES (?, 'Asia/Ho_Chi_Minh')", user);
        wallet = wallet(user, 0);
    }

    @Test void twoConcurrentExpensesOnlyOneFits() throws Exception {
        jdbc.update("UPDATE fintrack.wallets SET opening_balance_minor = 100000 WHERE id = ?", wallet);
        var outcomes = race(() -> outcome(() -> create(expense(70_000))), () -> outcome(() -> create(expense(70_000))));
        assertThat(outcomes).containsExactlyInAnyOrder("CREATED", "WALLET_INSUFFICIENT_FUNDS");
        assertThat(balance(wallet)).isEqualTo(30_000);
    }

    @Test void deleteIncomeRacingAnExpenseNeverGoesNegative() throws Exception {
        var income = create(income(100_000));
        var outcomes = race(() -> outcome(() -> { tx.executeWithoutResult(s -> service.delete(user, income.transactionId())); return "VOIDED"; }),
                () -> outcome(() -> create(expense(60_000))));
        assertThat(outcomes).contains("WALLET_INSUFFICIENT_FUNDS").containsAnyOf("VOIDED", "CREATED");
        assertThat(balance(wallet)).isGreaterThanOrEqualTo(0);
    }

    @Test void expenseEditAndDeleteReverseExactlyOnce() {
        jdbc.update("UPDATE fintrack.wallets SET opening_balance_minor = 1000000 WHERE id = ?", wallet);
        var created = create(expense(100_000));
        assertThat(balance(wallet)).isEqualTo(900_000);
        tx.execute(s -> service.update(user, created.transactionId(), expense(150_000)));
        assertThat(balance(wallet)).isEqualTo(850_000);
        tx.executeWithoutResult(s -> service.delete(user, created.transactionId()));
        var voidedAt = service.detail(user, created.transactionId()).voidedAt();
        tx.executeWithoutResult(s -> service.delete(user, created.transactionId()));
        assertThat(balance(wallet)).isEqualTo(1_000_000);
        var detail = service.detail(user, created.transactionId());
        assertThat(detail.status()).isEqualTo("VOIDED");
        assertThat(detail.voidedAt()).isEqualTo(voidedAt);
        assertThat(entries(created.transactionId())).isEqualTo(1);
        assertCode(() -> tx.execute(s -> service.update(user, created.transactionId(), expense(1))), "TRANSACTION_VOIDED");
    }

    @Test void voidingSpentIncomeIsRejectedUntilTheExpenseIsVoided() {
        var income = create(income(100_000));
        var expense = create(expense(60_000));
        assertCode(() -> tx.executeWithoutResult(s -> service.delete(user, income.transactionId())), "WALLET_INSUFFICIENT_FUNDS");
        assertThat(balance(wallet)).isEqualTo(40_000);
        assertThat(service.detail(user, income.transactionId()).status()).isEqualTo("POSTED");
        assertCode(() -> tx.execute(s -> service.update(user, income.transactionId(), income(50_000))), "WALLET_INSUFFICIENT_FUNDS");
        tx.executeWithoutResult(s -> service.delete(user, expense.transactionId()));
        assertThat(balance(wallet)).isEqualTo(100_000);
        tx.executeWithoutResult(s -> service.delete(user, income.transactionId()));
        assertThat(balance(wallet)).isEqualTo(0);
    }

    @Test void movingExpenseToAnotherWalletChargesNewAndRefundsOld() {
        UUID second = wallet(user, 50_000);
        var income = create(income(100_000));
        var expense = create(expense(80_000));
        TransactionRequest move = expense(80_000); move.walletId = second;
        assertCode(() -> tx.execute(s -> service.update(user, expense.transactionId(), move)), "WALLET_INSUFFICIENT_FUNDS");
        assertThat(balance(wallet)).isEqualTo(20_000);
        move.amountMinor = 50_000L;
        tx.execute(s -> service.update(user, expense.transactionId(), move));
        assertThat(balance(wallet)).isEqualTo(100_000);
        assertThat(balance(second)).isEqualTo(0);
        assertThat(income.status()).isEqualTo("POSTED");
    }

    @Test void replayReturnsCurrentRecordAfterEditAndVoid() {
        TransactionRequest original = income(100_000);
        var first = tx.execute(s -> service.create(user, original));
        assertThat(first.created()).isTrue();
        var replay = tx.execute(s -> service.create(user, original));
        assertThat(replay.created()).isFalse();
        assertThat(replay.body().transactionId()).isEqualTo(first.body().transactionId());
        tx.execute(s -> service.update(user, first.body().transactionId(), income(120_000)));
        assertThat(tx.execute(s -> service.create(user, original)).body().amountMinor()).isEqualTo(120_000);
        tx.executeWithoutResult(s -> service.delete(user, first.body().transactionId()));
        jdbc.update("UPDATE fintrack.wallets SET archived_at = now() WHERE id = ?", wallet);
        var afterVoid = tx.execute(s -> service.create(user, original));
        assertThat(afterVoid.created()).isFalse();
        assertThat(afterVoid.body().status()).isEqualTo("VOIDED");
        TransactionRequest different = income(100_001); different.clientRequestId = original.clientRequestId;
        assertCode(() -> tx.execute(s -> service.create(user, different)), "TRANSACTION_REQUEST_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fintrack.transactions WHERE user_id = ?", Long.class, user)).isEqualTo(1);
        // Another user's identical key is independent.
        UUID otherWallet = wallet(otherUser, 0); UUID otherCategory = category(otherUser, "INCOME");
        TransactionRequest foreignKey = income(1); foreignKey.walletId = otherWallet; foreignKey.categoryId = otherCategory;
        foreignKey.clientRequestId = original.clientRequestId;
        assertThat(tx.execute(s -> service.create(otherUser, foreignKey)).created()).isTrue();
    }

    @Test void concurrentRetriesWithSameKeyPostOnce() throws Exception {
        TransactionRequest request = income(10_000);
        Callable<String> post = () -> outcome(() -> tx.execute(s -> service.create(user, request)).created() ? "CREATED" : "REPLAY");
        assertThat(race(post, post)).containsExactlyInAnyOrder("CREATED", "REPLAY");
        assertThat(balance(wallet)).isEqualTo(10_000);
    }

    @Test void ownershipIsEnforcedEverywhere() {
        var mine = create(income(1_000));
        UUID foreignWallet = wallet(otherUser, 1_000_000);
        for (Runnable action : List.<Runnable>of(
                () -> service.detail(otherUser, mine.transactionId()),
                () -> tx.execute(s -> service.update(otherUser, mine.transactionId(), income(1))),
                () -> tx.executeWithoutResult(s -> service.delete(otherUser, mine.transactionId())),
                () -> service.list(user, 0, 50, null, null, foreignWallet, null, null, null, null),
                () -> { TransactionRequest r = income(1); r.walletId = foreignWallet; tx.execute(s -> service.create(user, r)); }))
            assertCode(action, "NOT_FOUND");
        assertThat(service.list(otherUser, 0, 50, null, null, null, null, null, null, null).items()).isEmpty();
        assertThat(service.detail(user, mine.transactionId()).status()).isEqualTo("POSTED");
    }

    @Test void dateFilterUsesUserTimezoneBoundaries() {
        jdbc.update("UPDATE fintrack.wallets SET opening_balance_minor = 1000000 WHERE id = ?", wallet);
        for (String at : new String[]{"2026-10-31T16:59:59Z", "2026-10-31T17:00:00Z", "2026-11-30T16:59:59Z", "2026-11-30T17:00:00Z"}) {
            TransactionRequest r = expense(1_000); r.occurredAt = at; create(r);
        }
        var november = service.list(user, 0, 50, "2026-11-01", "2026-11-30", null, null, null, null, "occurredAt,asc");
        assertThat(november.items()).extracting(t -> t.occurredAt().toString())
                .containsExactly("2026-10-31T17:00:00Z", "2026-11-30T16:59:59Z");
        jdbc.update("UPDATE fintrack.user_preferences SET time_zone = 'UTC' WHERE user_id = ?", user);
        assertThat(service.list(user, 0, 50, "2026-11-01", "2026-11-30", null, null, null, null, null).items())
                .extracting(t -> t.occurredAt().toString()).containsExactly("2026-11-30T17:00:00Z", "2026-11-30T16:59:59Z");
    }

    @Test void searchIsLiteralAndListPagesOnlyPosted() {
        jdbc.update("UPDATE fintrack.wallets SET opening_balance_minor = 1000000 WHERE id = ?", wallet);
        for (String note : new String[]{"Giảm 50% OFF", "Giảm 500 off", "a_b", "axb"}) {
            TransactionRequest r = expense(1_000); r.note = note; create(r);
        }
        TransactionRequest payee = expense(5_000); payee.payee = "Quán Đồng"; create(payee);
        var voided = create(expense(7_000));
        tx.executeWithoutResult(s -> service.delete(user, voided.transactionId()));
        assertThat(search("50%")).containsExactly("Giảm 50% OFF");
        assertThat(search("off")).containsExactlyInAnyOrder("Giảm 50% OFF", "Giảm 500 off");
        assertThat(search("_")).containsExactly("a_b");
        assertThat(service.list(user, 0, 50, null, null, null, null, null, "quán", null).items()).hasSize(1);
        var first = service.list(user, 0, 2, null, null, wallet, expenseCategory, "EXPENSE", null, "amountMinor,desc");
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.items().get(0).amountMinor()).isEqualTo(5_000);
        var all = service.list(user, 0, 50, null, null, null, null, null, null, "amountMinor,desc").items();
        var paged = new java.util.ArrayList<>(first.items());
        paged.addAll(service.list(user, 1, 2, null, null, null, null, null, null, "amountMinor,desc").items());
        paged.addAll(service.list(user, 2, 2, null, null, null, null, null, null, "amountMinor,desc").items());
        assertThat(paged).isEqualTo(all);
        // Equal amounts tie-break by id in the same direction (PostgreSQL uuid order == lowercase hex order).
        var ties = all.subList(1, 5).stream().map(t -> t.transactionId().toString()).toList();
        assertThat(ties).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        var beyond = service.list(user, 9, 2, null, null, null, null, null, null, null);
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.totalPages()).isEqualTo(3);
        assertThat(service.list(user, 0, 50, null, null, null, null, "INCOME", null, null).items()).isEmpty();
    }

    @Test void transferRacingAnExpenseOnTheSameSourceNeverGoesNegative() throws Exception {
        jdbc.update("UPDATE fintrack.wallets SET opening_balance_minor = 100000 WHERE id = ?", wallet);
        UUID savings = wallet(user, 0);
        var outcomes = race(() -> outcome(() -> create(transfer(wallet, savings, 70_000))),
                () -> outcome(() -> create(expense(70_000))));
        assertThat(outcomes).containsExactlyInAnyOrder("CREATED", "WALLET_INSUFFICIENT_FUNDS");
        assertThat(balance(wallet)).isEqualTo(30_000);
        assertThat(balance(wallet) + balance(savings)).isIn(30_000L, 100_000L);
    }

    @Test void transferMovesMoneyEditsAndVoidsExactlyOnce() {
        jdbc.update("UPDATE fintrack.wallets SET opening_balance_minor = 100000 WHERE id = ?", wallet);
        UUID savings = wallet(user, 0), third = wallet(user, 0);
        var created = create(transfer(wallet, savings, 60_000));
        assertThat(created.destinationWalletId()).isEqualTo(savings);
        assertThat(created.categoryId()).isNull();
        assertThat(entries(created.transactionId())).isEqualTo(2);
        assertThat(balance(wallet)).isEqualTo(40_000);
        assertThat(balance(savings)).isEqualTo(60_000);
        // Edit: change amount and destination; the old destination gives the money back.
        tx.execute(s -> service.update(user, created.transactionId(), transfer(wallet, third, 90_000)));
        assertThat(List.of(balance(wallet), balance(savings), balance(third))).containsExactly(10_000L, 0L, 90_000L);
        // Edit into a transfer that would empty the source below zero is rejected and changes nothing.
        assertCode(() -> tx.execute(s -> service.update(user, created.transactionId(), transfer(wallet, third, 100_001))),
                "WALLET_INSUFFICIENT_FUNDS");
        // Spend from the destination, then voiding the transfer would make it negative.
        TransactionRequest spend = expense(1); spend.walletId = third; var spent = create(spend);
        assertCode(() -> tx.executeWithoutResult(s -> service.delete(user, created.transactionId())), "WALLET_INSUFFICIENT_FUNDS");
        assertThat(service.detail(user, created.transactionId()).status()).isEqualTo("POSTED");
        tx.executeWithoutResult(s -> service.delete(user, spent.transactionId()));
        tx.executeWithoutResult(s -> service.delete(user, created.transactionId()));
        tx.executeWithoutResult(s -> service.delete(user, created.transactionId()));
        assertThat(List.of(balance(wallet), balance(savings), balance(third))).containsExactly(100_000L, 0L, 0L);
        assertThat(entries(created.transactionId())).isEqualTo(2);
        var voided = service.detail(user, created.transactionId());
        assertThat(voided.status()).isEqualTo("VOIDED");
        assertThat(voided.walletId()).isEqualTo(wallet);
        assertThat(voided.destinationWalletId()).isEqualTo(third);
    }

    @Test void expenseCanBecomeATransferAndBack() {
        jdbc.update("UPDATE fintrack.wallets SET opening_balance_minor = 100000 WHERE id = ?", wallet);
        UUID savings = wallet(user, 0);
        var created = create(expense(30_000));
        tx.execute(s -> service.update(user, created.transactionId(), transfer(wallet, savings, 30_000)));
        assertThat(List.of(balance(wallet), balance(savings))).containsExactly(70_000L, 30_000L);
        assertThat(entries(created.transactionId())).isEqualTo(2);
        tx.execute(s -> service.update(user, created.transactionId(), expense(30_000)));
        assertThat(List.of(balance(wallet), balance(savings))).containsExactly(70_000L, 0L);
        assertThat(entries(created.transactionId())).isEqualTo(1);
    }

    @Test void transferFiltersAndReplay() {
        jdbc.update("UPDATE fintrack.wallets SET opening_balance_minor = 100000 WHERE id = ?", wallet);
        UUID savings = wallet(user, 0), unrelated = wallet(user, 0);
        TransactionRequest original = transfer(wallet, savings, 10_000);
        var first = tx.execute(s -> service.create(user, original));
        assertThat(tx.execute(s -> service.create(user, original)).created()).isFalse();
        TransactionRequest otherDestination = transfer(wallet, unrelated, 10_000);
        otherDestination.clientRequestId = original.clientRequestId;
        assertCode(() -> tx.execute(s -> service.create(user, otherDestination)), "TRANSACTION_REQUEST_CONFLICT");
        create(expense(1_000));
        assertThat(service.list(user, 0, 50, null, null, null, null, "TRANSFER", null, null).items())
                .extracting(TransactionResponse::transactionId).containsExactly(first.body().transactionId());
        assertThat(service.list(user, 0, 50, null, null, savings, null, null, null, null).items()).hasSize(1);
        assertThat(service.list(user, 0, 50, null, null, wallet, null, null, null, null).items()).hasSize(2);
        assertThat(service.list(user, 0, 50, null, null, unrelated, null, null, null, null).items()).isEmpty();
        assertThat(service.list(user, 0, 50, null, null, null, expenseCategory, null, null, null).items()).hasSize(1);
    }

    @Test void transferRejectsForeignArchivedAndCategory() {
        jdbc.update("UPDATE fintrack.wallets SET opening_balance_minor = 100000 WHERE id = ?", wallet);
        UUID foreign = wallet(otherUser, 0), archived = wallet(user, 0);
        jdbc.update("UPDATE fintrack.wallets SET archived_at = now() WHERE id = ?", archived);
        assertCode(() -> create(transfer(wallet, foreign, 1)), "NOT_FOUND");
        assertCode(() -> create(transfer(wallet, archived, 1)), "WALLET_ARCHIVED");
        TransactionRequest withCategory = transfer(wallet, wallet(user, 0), 1); withCategory.categoryId = expenseCategory;
        assertCode(() -> create(withCategory), "VALIDATION_ERROR");
        assertThat(balance(wallet)).isEqualTo(100_000);
    }

    private TransactionRequest transfer(UUID source, UUID destination, long amount) {
        TransactionRequest r = request("TRANSFER", null, amount);
        r.walletId = source; r.destinationWalletId = destination;
        return r;
    }
    private List<String> search(String q) {
        return service.list(user, 0, 50, null, null, null, null, null, q, null).items().stream().map(TransactionResponse::note).toList();
    }
    private List<String> race(Callable<String> first, Callable<String> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<String> a = () -> { start.await(10, TimeUnit.SECONDS); return first.call(); };
            Callable<String> b = () -> { start.await(10, TimeUnit.SECONDS); return second.call(); };
            var fa = pool.submit(a); var fb = pool.submit(b); start.countDown();
            return List.of(fa.get(20, TimeUnit.SECONDS), fb.get(20, TimeUnit.SECONDS));
        }
    }
    private static String outcome(Callable<Object> action) {
        try { Object result = action.call(); return result instanceof String text ? text : "CREATED"; }
        catch (AppException error) { return error.getErrorCode().name(); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private TransactionResponse create(TransactionRequest request) { return tx.execute(s -> service.create(user, request)).body(); }
    private TransactionRequest expense(long amount) { return request("EXPENSE", expenseCategory, amount); }
    private TransactionRequest income(long amount) { return request("INCOME", incomeCategory, amount); }
    private TransactionRequest request(String type, UUID category, long amount) {
        TransactionRequest r = new TransactionRequest();
        r.transactionType = type; r.amountMinor = amount; r.categoryId = category; r.walletId = wallet;
        r.occurredAt = "2026-11-07T08:45:00Z"; r.clientRequestId = UUID.randomUUID();
        return r;
    }
    private long balance(UUID walletId) {
        return jdbc.queryForObject("SELECT balance_minor FROM fintrack.wallet_balances WHERE wallet_id = ?", Long.class, walletId);
    }
    private long entries(UUID transactionId) {
        return jdbc.queryForObject("SELECT count(*) FROM fintrack.transaction_entries WHERE transaction_id = ?", Long.class, transactionId);
    }
    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getErrorCode().name()).isEqualTo(code));
    }
    private UUID user() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO fintrack.users(id,username,email,password_hash) VALUES (?,?,?,'test-only')", id, "txn-" + id, id + "@txn.test");
        return id;
    }
    private UUID category(UUID owner, String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO fintrack.categories(id,user_id,name,category_type) VALUES (?,?,?,?)", id, owner, "Txn-" + id, type);
        return id;
    }
    private UUID wallet(UUID owner, long opening) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO fintrack.wallets(id,user_id,name,wallet_type,opening_balance_minor) VALUES (?,?,?,'CASH',?)",
                id, owner, "Txn-" + id, opening);
        return id;
    }
}
