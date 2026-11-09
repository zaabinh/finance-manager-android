package com.fintrack.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.api.entity.enums.UserRole;
import com.fintrack.api.service.auth.RateLimitingService;
import com.fintrack.api.service.auth.TokenService;
import com.fintrack.api.service.category.CategorySeedService;
import com.fintrack.api.service.notification.EmailSenderService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testcontainers.containers.PostgreSQLContainer;

/** Real JWT/security/HTTP/DB integration; mail and rate-limit infrastructure are isolated from finance behavior. */
@SpringBootTest(properties = {"spring.config.import=", "logging.level.root=WARN",
        "management.health.redis.enabled=false", "management.endpoint.health.group.readiness.include=readinessState,db"})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class FinanceApiIT {
    static PostgreSQLContainer<?> postgres;
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        String external = System.getenv("FINANCE_TEST_JDBC_URL");
        if (external == null || external.isBlank()) {
            postgres = new PostgreSQLContainer<>("postgres:17.7"); postgres.start();
            properties.add("spring.datasource.url", postgres::getJdbcUrl);
            properties.add("spring.datasource.username", postgres::getUsername);
            properties.add("spring.datasource.password", postgres::getPassword);
        } else {
            properties.add("spring.datasource.url", () -> external);
            properties.add("spring.datasource.username", () -> System.getenv("FINANCE_TEST_DB_USER"));
            properties.add("spring.datasource.password", () -> System.getenv("FINANCE_TEST_DB_PASSWORD"));
        }
    }
    @AfterAll static void stop() { if (postgres != null) postgres.stop(); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TokenService tokens;
    @Autowired CategorySeedService seeds;
    @MockitoBean RateLimitingService rateLimits;
    @MockitoBean EmailSenderService mail;
    String token, otherToken, expense, income;
    UUID user;

    @BeforeEach void fixtures() {
        user = user();
        token = tokens.createAccessToken(user, UserRole.USER);
        otherToken = tokens.createAccessToken(user(), UserRole.USER);
        expense = jdbc.queryForObject("SELECT id FROM fintrack.categories WHERE user_id=? AND name='Ăn uống'", UUID.class, user).toString();
        income = jdbc.queryForObject("SELECT id FROM fintrack.categories WHERE user_id=? AND name='Lương'", UUID.class, user).toString();
    }
    private UUID user() {
        String name = "finance_" + UUID.randomUUID().toString().replace("-", "");
        UUID id = jdbc.queryForObject("INSERT INTO fintrack.users(username,email,password_hash,email_verified) VALUES (?,?,'test-only',true) RETURNING id",
                UUID.class, name, name + "@example.test");
        seeds.initialize(id);
        jdbc.update("INSERT INTO fintrack.notification_settings(user_id) VALUES (?) ON CONFLICT DO NOTHING", id);
        return id;
    }
    private MockHttpServletResponse call(String method, String path, String auth, String body) throws Exception {
        var builder = request(HttpMethod.valueOf(method), path).contentType(MediaType.APPLICATION_JSON);
        if (auth != null) builder.header("Authorization", "Bearer " + auth);
        if (body != null) builder.content(body);
        return mvc.perform(builder).andReturn().getResponse();
    }
    private JsonNode ok(MockHttpServletResponse response, int expected) throws Exception {
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expected);
        return response.getContentAsString().isBlank() ? null : json.readTree(response.getContentAsString());
    }
    private String wallet(long opening) throws Exception {
        return ok(call("POST", "/api/v1/wallets", token, json.writeValueAsString(Map.of("name", UUID.randomUUID().toString(),
                "walletType", "CASH", "openingBalanceMinor", opening))), 201).get("walletId").asText();
    }
    private String posting(String wallet, String category, String type, long amount) throws Exception {
        return json.writeValueAsString(Map.of("walletId", wallet, "categoryId", category, "transactionType", type,
                "amountMinor", amount, "occurredAt", "2026-11-08T05:00:00Z", "clientRequestId", UUID.randomUUID().toString()));
    }
    private long balance(String wallet) throws Exception {
        return ok(call("GET", "/api/v1/wallets/" + wallet, token, null), 200).get("balanceMinor").asLong();
    }

    @Test void allFinanceCollectionsRequireAuthentication() throws Exception {
        for (String module : List.of("wallets", "categories", "budgets", "transactions"))
            assertThat(call("GET", "/api/v1/" + module, null, null).getStatus()).isEqualTo(401);
    }

    @Test void realJwtCannotReadOrMutateOtherOwnersWalletOrCategory() throws Exception {
        String wallet = wallet(100_000);
        for (String method : List.of("GET", "DELETE")) {
            ok(call(method, "/api/v1/wallets/" + wallet, otherToken, null), 404);
            ok(call(method, "/api/v1/categories/" + expense, otherToken, null), 404);
        }
        ok(call("POST", "/api/v1/transactions", otherToken, posting(wallet, expense, "EXPENSE", 1)), 404);
        assertThat(balance(wallet)).isEqualTo(100_000);
    }

    @Test void strictFinanceJsonDoesNotCoerceMoneyBooleansOrIgnoreOwnerFields() throws Exception {
        ok(call("POST", "/api/v1/wallets", token,
                "{\"name\":\"Fraction\",\"walletType\":\"CASH\",\"openingBalanceMinor\":1.5}"), 422);
        ok(call("POST", "/api/v1/wallets", token,
                "{\"name\":\"Coerced\",\"walletType\":\"CASH\",\"includeInAssets\":\"true\"}"), 422);
        ok(call("POST", "/api/v1/categories", token,
                "{\"name\":\"Spoof\",\"categoryType\":\"EXPENSE\",\"userId\":\"" + user + "\"}"), 400);
    }

    @Test void expenseEditVoidAndBudgetUsageReconcileThroughHttp() throws Exception {
        String wallet = wallet(1_000_000);
        JsonNode budget = ok(call("POST", "/api/v1/budgets", token, json.writeValueAsString(Map.of(
                "categoryId", expense, "month", "2026-11", "limitMinor", 100_000))), 201);
        String body = posting(wallet, expense, "EXPENSE", 100_000);
        String transaction = ok(call("POST", "/api/v1/transactions", token, body), 201).get("transactionId").asText();
        assertThat(balance(wallet)).isEqualTo(900_000);
        ok(call("PUT", "/api/v1/transactions/" + transaction, token, posting(wallet, expense, "EXPENSE", 150_000)), 200);
        assertThat(balance(wallet)).isEqualTo(850_000);
        JsonNode usage = ok(call("GET", "/api/v1/budgets/" + budget.get("budgetId").asText(), token, null), 200);
        assertThat(usage.get("spentMinor").asLong()).isEqualTo(150_000);
        assertThat(usage.get("remainingMinor").asLong()).isEqualTo(-50_000);
        assertThat(usage.get("status").asText()).isEqualTo("EXCEEDED");
        ok(call("DELETE", "/api/v1/transactions/" + transaction, token, null), 204);
        ok(call("DELETE", "/api/v1/transactions/" + transaction, token, null), 204);
        assertThat(balance(wallet)).isEqualTo(1_000_000);
        assertThat(ok(call("GET", "/api/v1/budgets/" + budget.get("budgetId").asText(), token, null), 200)
                .get("spentMinor").asLong()).isZero();
        assertThat(ok(call("POST", "/api/v1/transactions", token, body), 200).get("status").asText()).isEqualTo("VOIDED");
        assertThat(balance(wallet)).isEqualTo(1_000_000);
    }

    @Test void incomeVoidThatWouldGoNegativeIsRejectedAtomically() throws Exception {
        String wallet = wallet(0);
        String id = ok(call("POST", "/api/v1/transactions", token, posting(wallet, income, "INCOME", 100_000)), 201)
                .get("transactionId").asText();
        String spend = ok(call("POST", "/api/v1/transactions", token, posting(wallet, expense, "EXPENSE", 60_000)), 201)
                .get("transactionId").asText();
        JsonNode error = ok(call("DELETE", "/api/v1/transactions/" + id, token, null), 409);
        assertThat(error.get("errorCode").asText()).isEqualTo("WALLET_INSUFFICIENT_FUNDS");
        assertThat(balance(wallet)).isEqualTo(40_000);
        assertThat(ok(call("GET", "/api/v1/transactions/" + id, token, null), 200).get("status").asText()).isEqualTo("POSTED");
        ok(call("DELETE", "/api/v1/transactions/" + spend, token, null), 204);
        ok(call("DELETE", "/api/v1/transactions/" + id, token, null), 204);
        assertThat(balance(wallet)).isZero();
    }

    @Test void archivePreservesHistoryButBlocksNewPostingAndSeedDoesNotReappear() throws Exception {
        String wallet = wallet(100_000);
        String id = ok(call("POST", "/api/v1/transactions", token, posting(wallet, expense, "EXPENSE", 10_000)), 201)
                .get("transactionId").asText();
        ok(call("DELETE", "/api/v1/categories/" + expense, token, null), 204);
        ok(call("DELETE", "/api/v1/wallets/" + wallet, token, null), 204);
        assertThat(balance(wallet)).isEqualTo(90_000);
        ok(call("POST", "/api/v1/transactions", token, posting(wallet, expense, "EXPENSE", 1)), 409);
        seeds.initialize(user);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fintrack.categories WHERE user_id=?", Integer.class, user)).isEqualTo(8);
        ok(call("DELETE", "/api/v1/transactions/" + id, token, null), 204);
        assertThat(balance(wallet)).isEqualTo(100_000);
        assertThat(ok(call("GET", "/api/v1/transactions/" + id, token, null), 200).get("status").asText()).isEqualTo("VOIDED");
    }

    @Test void walletOpeningReductionRacesWithExpenseUsingSameLock() throws Exception {
        String wallet = wallet(100_000);
        String edit = json.writeValueAsString(Map.of("name", "Race", "walletType", "CASH", "openingBalanceMinor", 50_000));
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var posting = pool.submit(() -> { start.await(); return call("POST", "/api/v1/transactions", token,
                    posting(wallet, expense, "EXPENSE", 70_000)).getStatus(); });
            var opening = pool.submit(() -> { start.await(); return call("PUT", "/api/v1/wallets/" + wallet, token, edit).getStatus(); });
            start.countDown();
            List<Integer> statuses = List.of(posting.get(20, TimeUnit.SECONDS), opening.get(20, TimeUnit.SECONDS));
            assertThat(statuses).contains(409);
            assertThat(statuses.stream().filter(status -> status == 200 || status == 201).count()).isEqualTo(1);
        }
        assertThat(balance(wallet)).isIn(30_000L, 50_000L);
    }
}
