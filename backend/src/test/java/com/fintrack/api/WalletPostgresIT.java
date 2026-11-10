package com.fintrack.api;

import static org.assertj.core.api.Assertions.*;

import com.fintrack.api.dto.request.transaction.TransactionRequest;
import com.fintrack.api.dto.request.wallet.WalletRequest;
import com.fintrack.api.exception.AppException;
import com.fintrack.api.repository.TransactionRepository;
import com.fintrack.api.repository.WalletRepository;
import com.fintrack.api.service.finance.FinanceSupport;
import com.fintrack.api.service.transaction.TransactionService;
import com.fintrack.api.service.wallet.WalletService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/** Opening-balance edits and transaction writers share the wallet row lock (contract §5). */
class WalletPostgresIT {
    static PostgreSQLContainer<?> postgres;
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;
    static WalletService wallets;
    static TransactionService transactions;

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
        FinanceSupport finance = new FinanceSupport(jdbc);
        wallets = new WalletService(new WalletRepository(jdbc), finance);
        transactions = new TransactionService(new TransactionRepository(jdbc), finance);
    }
    @AfterAll static void stop() { if (postgres != null) postgres.stop(); }

    @RepeatedTest(5) void openingEditRacingExpenseLeavesExactlyOneWinner() throws Exception {
        UUID user = UUID.randomUUID(), category = UUID.randomUUID(), wallet = UUID.randomUUID();
        jdbc.update("INSERT INTO fintrack.users(id,username,email,password_hash) VALUES (?,?,?,'test-only')", user, "wallet-" + user, user + "@wallet.test");
        jdbc.update("INSERT INTO fintrack.categories(id,user_id,name,category_type) VALUES (?,?,'Ăn uống','EXPENSE')", category, user);
        jdbc.update("INSERT INTO fintrack.wallets(id,user_id,name,wallet_type,opening_balance_minor) VALUES (?,?,'Tiền mặt','CASH',100000)", wallet, user);
        WalletRequest opening = new WalletRequest(); opening.name = "Tiền mặt"; opening.walletType = "CASH"; opening.openingBalanceMinor = 30_000L;
        TransactionRequest expense = new TransactionRequest();
        expense.transactionType = "EXPENSE"; expense.amountMinor = 70_000L; expense.categoryId = category; expense.walletId = wallet;
        expense.occurredAt = "2026-11-07T08:45:00Z"; expense.clientRequestId = UUID.randomUUID();

        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var edit = pool.submit(attempt(start, () -> tx.execute(s -> wallets.update(user, wallet, opening))));
            var spend = pool.submit(attempt(start, () -> tx.execute(s -> transactions.create(user, expense))));
            start.countDown();
            assertThat(List.of(edit.get(20, TimeUnit.SECONDS), spend.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", "WALLET_INSUFFICIENT_FUNDS");
        }
        long balance = jdbc.queryForObject("SELECT balance_minor FROM fintrack.wallet_balances WHERE wallet_id = ?", Long.class, wallet);
        assertThat(balance).isEqualTo(30_000); // expense won: 100k-70k; opening won: 30k with no expense
    }

    private static Callable<String> attempt(CountDownLatch start, Runnable action) {
        return () -> {
            start.await(10, TimeUnit.SECONDS);
            try { action.run(); return "OK"; }
            catch (AppException error) { return error.getErrorCode().name(); }
        };
    }
}
