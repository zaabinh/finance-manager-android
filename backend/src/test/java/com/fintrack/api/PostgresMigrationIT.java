package com.fintrack.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.ArrayList;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PostgresMigrationIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7");

    @Test
    void walletRenamePreservesExistingPostingsAndBalances() throws Exception {
        try (var upgradeDatabase = new PostgreSQLContainer<>("postgres:17.7")) {
            upgradeDatabase.start();
            Flyway.configure().dataSource(upgradeDatabase.getJdbcUrl(), upgradeDatabase.getUsername(),
                    upgradeDatabase.getPassword()).defaultSchema("fintrack").target("2").load().migrate();
            try (var connection = DriverManager.getConnection(upgradeDatabase.getJdbcUrl(),
                    upgradeDatabase.getUsername(), upgradeDatabase.getPassword());
                 var statement = connection.createStatement()) {
                connection.setAutoCommit(false);
                statement.execute("""
                        INSERT INTO fintrack.users(id, username, email, password_hash)
                        VALUES ('00000000-0000-0000-0000-000000000001', 'wallet_upgrade',
                            'wallet-upgrade@example.test', 'test-only-hash');
                        INSERT INTO fintrack.accounts(id, user_id, name, account_type, opening_balance_minor)
                        VALUES ('00000000-0000-0000-0000-000000000002',
                            '00000000-0000-0000-0000-000000000001', 'Migration wallet', 'CASH', 1000000);
                        INSERT INTO fintrack.transactions(id, user_id, transaction_type, amount_minor,
                            adjustment_direction, occurred_at)
                        VALUES ('00000000-0000-0000-0000-000000000003',
                            '00000000-0000-0000-0000-000000000001', 'ADJUSTMENT', 125000, 'IN', now());
                        INSERT INTO fintrack.transaction_entries(user_id, transaction_id, account_id, delta_minor)
                        VALUES ('00000000-0000-0000-0000-000000000001',
                            '00000000-0000-0000-0000-000000000003',
                            '00000000-0000-0000-0000-000000000002', 125000);
                        """);
                connection.commit();
            }
            var flyway = Flyway.configure().dataSource(upgradeDatabase.getJdbcUrl(), upgradeDatabase.getUsername(),
                    upgradeDatabase.getPassword()).defaultSchema("fintrack").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            try (var connection = DriverManager.getConnection(upgradeDatabase.getJdbcUrl(),
                    upgradeDatabase.getUsername(), upgradeDatabase.getPassword());
                 var statement = connection.createStatement()) {
                try (var result = statement.executeQuery("""
                        SELECT wallet.wallet_type, balance.balance_minor, entry.delta_minor
                        FROM fintrack.wallets wallet
                        JOIN fintrack.wallet_balances balance ON balance.wallet_id = wallet.id
                        JOIN fintrack.transaction_entries entry ON entry.wallet_id = wallet.id
                        WHERE wallet.id = '00000000-0000-0000-0000-000000000002'
                        """)) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getString("wallet_type")).isEqualTo("CASH");
                    assertThat(result.getBigDecimal("balance_minor")).isEqualByComparingTo("1125000");
                    assertThat(result.getLong("delta_minor")).isEqualTo(125000);
                    assertThat(result.next()).isFalse();
                }
                try (var result = statement.executeQuery("""
                        SELECT count(*) FROM pg_constraint
                        WHERE connamespace = 'fintrack'::regnamespace AND conname LIKE '%account%'
                        """)) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).isZero();
                }
            }
            assertThat(flyway.migrate().migrationsExecuted).isZero();
        }
    }

    @Test
    void migratesOnceAndRestartsWithHealthyDatabase() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            try (var context = new SpringApplicationBuilder(FinanceManagerApplication.class).run(
                    "--spring.profiles.active=dev",
                    "--server.port=0",
                    "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                    "--spring.datasource.username=" + POSTGRES.getUsername(),
                    "--spring.datasource.password=" + POSTGRES.getPassword())) {
                var webContext = (ServletWebServerApplicationContext) context;
                int port = webContext.getWebServer().getPort();
                try (var client = HttpClient.newHttpClient()) {
                    for (String path : new String[]{"/actuator/health", "/actuator/health/readiness",
                            "/swagger-ui/index.html", "/v3/api-docs"}) {
                        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
                        assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
                    }
                }
                var flyway = context.getBean(Flyway.class);
                assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("4");
                assertThat(flyway.migrate().migrationsExecuted).isZero();
                try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                        POSTGRES.getPassword());
                     var statement = connection.createStatement();
                     var result = statement.executeQuery(
                             "SELECT count(*) FROM fintrack.flyway_schema_history WHERE version IN ('1', '2', '3', '4') AND success")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).isEqualTo(4);
                }
                try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                        POSTGRES.getPassword());
                     var statement = connection.createStatement()) {
                    var tables = new ArrayList<String>();
                    try (var result = statement.executeQuery("""
                            SELECT table_name FROM information_schema.tables
                            WHERE table_schema = 'fintrack' AND table_type = 'BASE TABLE'
                            AND table_name <> 'flyway_schema_history'
                            """)) {
                        while (result.next()) {
                            tables.add(result.getString(1));
                        }
                    }
                    assertThat(tables).containsExactlyInAnyOrder("users", "user_preferences", "refresh_sessions",
                            "wallets", "categories", "transactions", "transaction_entries", "budgets",
                            "verification_challenges");
                    try (var script = getClass().getResourceAsStream("/db/core-schema-smoke.sql")) {
                        assertThat(script).isNotNull();
                        statement.execute(new String(script.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            }
        }
    }
}
