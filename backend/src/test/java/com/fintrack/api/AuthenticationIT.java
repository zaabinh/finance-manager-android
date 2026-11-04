package com.fintrack.api;

import com.fintrack.api.dto.request.auth.*;
import com.fintrack.api.dto.response.auth.TokenResponse;
import com.fintrack.api.exception.AppException;
import com.fintrack.api.repository.UserRepository;
import com.fintrack.api.service.auth.*;
import com.fintrack.api.service.notification.EmailSenderService;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"logging.level.root=WARN", "logging.level.com.fintrack.api=WARN", "spring.config.import="})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@org.springframework.test.annotation.DirtiesContext
@org.testcontainers.junit.jupiter.Testcontainers
class AuthenticationIT {
    @org.testcontainers.junit.jupiter.Container
    private static final org.testcontainers.containers.GenericContainer<?> REDIS =
            new org.testcontainers.containers.GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    private static final String REDIS_PREFIX = "it-auth:" + UUID.randomUUID() + ':';
    private static PostgreSQLContainer<?> postgres;
    @org.junit.jupiter.api.AfterAll static void stopContainer() { if (postgres != null) postgres.stop(); }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.username", () -> "");
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.data.redis.ssl.enabled", () -> false);
        registry.add("app.auth.rate-limit-key-prefix", () -> REDIS_PREFIX);
        String url = System.getProperty("auth.it.jdbc-url");
        if (url == null) {
            postgres = new PostgreSQLContainer<>("postgres:17.7");
            postgres.start();
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> System.getProperty("auth.it.username", "postgres"));
            registry.add("spring.datasource.password", () -> System.getProperty("auth.it.password", ""));
        }
    }
    @Autowired AuthOperations auth;
    @Autowired TokenService tokens;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired Flyway flyway;
    @MockitoBean EmailSenderService mail;
    private final Map<String, String> codes = new ConcurrentHashMap<>();
    private final ClientMetadata client = new ClientMetadata("127.0.0.1", "integration-test");
    @BeforeEach void captureEmails() {
        doAnswer(call -> {
            String purpose = call.<String>getArgument(1).contains("mật khẩu") ? "PASSWORD_RESET" : "EMAIL_VERIFICATION";
            var code = java.util.regex.Pattern.compile(">(\\d{4})<").matcher(call.<String>getArgument(2));
            assertThat(code.find()).isTrue();
            codes.put(call.getArgument(0) + ":" + purpose, code.group(1));
            return null;
        }).when(mail).send(anyString(), anyString(), anyString(), any());
    }
    @Test void fullHttpFlowAndPersistentRotation() throws Exception {
        String name = "u" + UUID.randomUUID().toString().replace("-", "");
        String email = name + "@example.test";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"Strong@123\"}"))
                .andExpect(status().isCreated()).andExpect(content().string(""));
        assertThat(codes.get(email + ":EMAIL_VERIFICATION")).matches("[0-9]{4}");
        mvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"otp\":\"" + codes.get(email + ":EMAIL_VERIFICATION") + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"usernameOrEmail\":\"" + name + "\",\"password\":\"Strong@123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.displayName").value(name))
                .andExpect(jsonPath("$.data.token.tokenType").value("Bearer"));
        var login = auth.login(new LoginRequest(email, "Strong@123"), client);
        assertThat(tokens.authenticate(login.token().accessToken())).isPresent();
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + login.token().accessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.email").value(email));
        mvc.perform(get("/api/v1/users/me").header("Authorization", "bearer " + login.token().accessToken()))
                .andExpect(status().isOk());
        var rotated = auth.refresh(login.token().refreshToken(), client);
        assertThat(rotated.refreshToken()).isNotEqualTo(login.token().refreshToken());
        // Inside the grace period a replay of the rotated token returns the same, still unused successor.
        assertThat(auth.refresh(login.token().refreshToken(), client).refreshToken()).isEqualTo(rotated.refreshToken());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fintrack.refresh_sessions WHERE token_hash = ?", Integer.class,
                TokenHash.sha256(rotated.refreshToken()))).isEqualTo(1);
        auth.logout(rotated.refreshToken()); auth.logout(rotated.refreshToken());
        assertThatThrownBy(() -> auth.refresh(rotated.refreshToken(), client)).isInstanceOf(AppException.class);
        mvc.perform(get("/api/v1/wallets")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/wallets").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("AUTH_ACCESS_TOKEN_INVALID"));
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer token with spaces"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("AUTH_ACCESS_TOKEN_INVALID"));
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
    @Test void wrongOtpAttemptsCommitAndResetRevokesSessions() {
        String email = registeredVerified();
        var old = auth.login(new LoginRequest(email, "Strong@123"), client).token();
        auth.forgotPassword(new ResendOtpRequest(email));
        String code = codes.get(email + ":PASSWORD_RESET");
        String wrong = code.equals("0000") ? "1111" : "0000";
        for (int i = 0; i < 5; i++) assertThatThrownBy(() -> auth.resetPassword(new ResetPasswordRequest(email, wrong, "NewStrong@456"))).isInstanceOf(AppException.class);
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM fintrack.verification_challenges WHERE email = ? AND purpose = 'PASSWORD_RESET' AND consumed_at IS NULL", Integer.class, email)).isEqualTo(5);
        assertThatThrownBy(() -> auth.resetPassword(new ResetPasswordRequest(email, code, "NewStrong@456"))).isInstanceOf(AppException.class);
        auth.forgotPassword(new ResendOtpRequest(email));
        String replacement = codes.get(email + ":PASSWORD_RESET");
        auth.resetPassword(new ResetPasswordRequest(email, replacement, "NewStrong@456"));
        assertThatThrownBy(() -> auth.resetPassword(new ResetPasswordRequest(email, replacement, "Other@123"))).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> auth.refresh(old.refreshToken(), client)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> auth.login(new LoginRequest(email, "Strong@123"), client)).isInstanceOf(AppException.class);
        assertThat(auth.login(new LoginRequest(email, "NewStrong@456"), client).token()).isNotNull();
    }
    @Test void concurrentRefreshRotatesOnceAndBothCallersGetTheSameSuccessor() throws Exception {
        String email = registeredVerified();
        var refresh = auth.login(new LoginRequest(email, "Strong@123"), client).token().refreshToken();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<String> task = () -> { start.await(); return auth.refresh(refresh, client).refreshToken(); };
            var one = pool.submit(task); var two = pool.submit(task); start.countDown();
            assertThat(one.get(20, TimeUnit.SECONDS)).isEqualTo(two.get(20, TimeUnit.SECONDS)).isNotEqualTo(refresh);
        }
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM fintrack.refresh_sessions s JOIN fintrack.users u ON u.id = s.user_id
                WHERE u.email = ? AND s.revoked_at IS NULL
                """, Integer.class, email)).isEqualTo(1);
    }
    @Test void changePasswordSignsOutEveryDeviceAndReturnsFreshPair() throws Exception {
        String email = registeredVerified();
        var phone = auth.login(new LoginRequest(email, "Strong@123"), client).token();
        var tablet = auth.login(new LoginRequest(email, "Strong@123"), client).token();
        auth.forgotPassword(new ResendOtpRequest(email));
        String pendingReset = codes.get(email + ":PASSWORD_RESET");
        assertThat(pendingReset).matches("[0-9]{4}");
        String body = mvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + phone.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Strong@123\",\"newPassword\":\"NewStrong@456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String fresh = com.jayway.jsonpath.JsonPath.read(body, "$.data.refreshToken");
        assertThatThrownBy(() -> auth.resetPassword(new ResetPasswordRequest(email, pendingReset, "Attacker@789")))
                .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getErrorCode())
                        .isEqualTo(com.fintrack.api.exception.ErrorCode.AUTH_OTP_INVALID));
        assertThat(auth.login(new LoginRequest(email, "NewStrong@456"), client).token()).isNotNull();
        assertThatThrownBy(() -> auth.refresh(phone.refreshToken(), client)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> auth.refresh(tablet.refreshToken(), client)).isInstanceOf(AppException.class);
        assertThat(auth.refresh(fresh, client).refreshToken()).isNotBlank();
        mvc.perform(post("/api/v1/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"NewStrong@456\",\"newPassword\":\"Other@789\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer invalid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"NewStrong@456\",\"newPassword\":\"Other@789\"}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void replayOfRotatedTokenRevokesWholeFamilyButNotOtherDevices() {
        String email = registeredVerified();
        var t0 = auth.login(new LoginRequest(email, "Strong@123"), client).token().refreshToken();
        var otherDevice = auth.login(new LoginRequest(email, "Strong@123"), client).token().refreshToken();
        var t1 = auth.refresh(t0, client).refreshToken();
        var t2 = auth.refresh(t1, client).refreshToken();
        // t1 is already used, so replaying t0 is theft even inside the grace period.
        assertThatThrownBy(() -> auth.refresh(t0, client)).isInstanceOfSatisfying(AppException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(com.fintrack.api.exception.ErrorCode.AUTH_REFRESH_TOKEN_INVALID));
        // The revocation committed although the request failed: the legitimate holder of t2 is signed out too.
        assertThatThrownBy(() -> auth.refresh(t2, client)).isInstanceOf(AppException.class);
        assertThat(jdbc.queryForObject("SELECT revoke_reason FROM fintrack.refresh_sessions WHERE token_hash = ?",
                String.class, TokenHash.sha256(t2))).isEqualTo("REUSE_DETECTED");
        assertThat(auth.refresh(otherDevice, client).refreshToken()).isNotBlank();
    }
    @Test void duplicateIdentityAndUnknownEmailHaveExpectedContracts() throws Exception {
        String email = registeredVerified();
        assertThatThrownBy(() -> auth.register(new RegisterRequest("another-user", email, "Strong@123"))).isInstanceOf(AppException.class);
        mvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"missing@example.test\"}")).andExpect(status().isAccepted()).andExpect(content().string(""));
        mvc.perform(post("/api/v1/auth/resend-verification").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"another-missing@example.test\"}")).andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"missing@example.test\",\"otp\":\"1234\"}")).andExpect(status().isBadRequest());
    }
    @Test void v4UpgradesExistingV3DataAndRestartsWithoutChangingHistory() throws Exception {
        String database = "auth_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        String url;
        try (var connection = jdbc.getDataSource().getConnection()) { url = connection.getMetaData().getURL(); }
        String upgradeUrl = url.substring(0, url.lastIndexOf('/') + 1) + database;
        String username;
        try (var connection = jdbc.getDataSource().getConnection()) { username = connection.getMetaData().getUserName(); }
        String password = postgres == null ? System.getProperty("auth.it.password", "") : postgres.getPassword();
        jdbc.execute("CREATE DATABASE " + database);
        try {
            Flyway.configure().dataSource(upgradeUrl, username, password).defaultSchema("fintrack").target("3").load().migrate();
            try (var connection = java.sql.DriverManager.getConnection(upgradeUrl, username, password);
                 var statement = connection.createStatement()) {
                statement.execute("INSERT INTO fintrack.users(username,email,password_hash) VALUES ('existing','existing@example.test','old-hash')");
            }
            var upgrade = Flyway.configure().dataSource(upgradeUrl, username, password).defaultSchema("fintrack").load();
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(2);
            assertThat(upgrade.migrate().migrationsExecuted).isZero();
            try (var connection = java.sql.DriverManager.getConnection(upgradeUrl, username, password);
                 var statement = connection.createStatement()) {
                try (var result = statement.executeQuery("SELECT email_verified,onboarding_completed,password_hash FROM fintrack.users WHERE username='existing'")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getBoolean("email_verified")).isFalse();
                    assertThat(result.getBoolean("onboarding_completed")).isFalse();
                    assertThat(result.getString("password_hash")).isEqualTo("old-hash");
                }
                try (var result = statement.executeQuery("SELECT count(*) FROM information_schema.columns WHERE table_schema='fintrack' AND table_name='users' AND column_name IN ('failed_login_attempts','locked_until','password_changed_at','email_verified_at')")) {
                    result.next(); assertThat(result.getInt(1)).isZero();
                }
            }
        } finally {
            // Only the unique database created by this test is removed.
            jdbc.execute("DROP DATABASE " + database);
        }
    }
    @Test void v5BackfillsOneFamilyPerExistingRotationChain() throws Exception {
        String database = "auth_v5_" + UUID.randomUUID().toString().replace("-", "");
        String url;
        String username;
        try (var connection = jdbc.getDataSource().getConnection()) {
            url = connection.getMetaData().getURL(); username = connection.getMetaData().getUserName();
        }
        String upgradeUrl = url.substring(0, url.lastIndexOf('/') + 1) + database;
        String password = postgres == null ? System.getProperty("auth.it.password", "") : postgres.getPassword();
        jdbc.execute("CREATE DATABASE " + database);
        try {
            Flyway.configure().dataSource(upgradeUrl, username, password).defaultSchema("fintrack").target("4").load().migrate();
            var old = new org.springframework.jdbc.core.JdbcTemplate(
                    new org.springframework.jdbc.datasource.DriverManagerDataSource(upgradeUrl, username, password));
            UUID user = old.queryForObject("INSERT INTO fintrack.users(username,email,password_hash) VALUES ('u','u@example.test','h') RETURNING id", UUID.class);
            // Chain a -> b -> c (c active) plus a standalone active session d.
            String insert = "INSERT INTO fintrack.refresh_sessions(user_id,token_hash,expires_at,revoked_at) VALUES (?,?,now()+interval '1 day',?) RETURNING id";
            UUID c = old.queryForObject(insert, UUID.class, user, "c".repeat(64), null);
            UUID b = old.queryForObject(insert, UUID.class, user, "b".repeat(64), new java.sql.Timestamp(System.currentTimeMillis()));
            UUID a = old.queryForObject(insert, UUID.class, user, "a".repeat(64), new java.sql.Timestamp(System.currentTimeMillis()));
            UUID d = old.queryForObject(insert, UUID.class, user, "d".repeat(64), null);
            old.update("UPDATE fintrack.refresh_sessions SET replaced_by_session_id = ? WHERE id = ?", b, a);
            old.update("UPDATE fintrack.refresh_sessions SET replaced_by_session_id = ? WHERE id = ?", c, b);
            assertThat(Flyway.configure().dataSource(upgradeUrl, username, password).defaultSchema("fintrack").load()
                    .migrate().migrationsExecuted).isEqualTo(1);
            Map<UUID, UUID> family = new HashMap<>();
            Map<UUID, String> reason = new HashMap<>();
            old.query("SELECT id, family_id, revoke_reason FROM fintrack.refresh_sessions", (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                family.put(rs.getObject("id", UUID.class), rs.getObject("family_id", UUID.class));
                reason.put(rs.getObject("id", UUID.class), rs.getString("revoke_reason"));
            });
            assertThat(family.get(a)).isEqualTo(a);
            assertThat(family.get(b)).isEqualTo(a);
            assertThat(family.get(c)).isEqualTo(a);
            assertThat(family.get(d)).isEqualTo(d);
            assertThat(reason.get(a)).isEqualTo("ROTATED");
            assertThat(reason.get(b)).isEqualTo("ROTATED");
            assertThat(reason.get(c)).isNull();
        } finally {
            jdbc.execute("DROP DATABASE " + database);
        }
    }
    private String registeredVerified() {
        String name = "u" + UUID.randomUUID().toString().replace("-", ""); String email = name + "@example.test";
        auth.register(new RegisterRequest(name, email, "Strong@123"));
        auth.verifyEmail(new VerifyEmailRequest(email, codes.get(email + ":EMAIL_VERIFICATION")));
        assertThat(users.findByEmail(email).orElseThrow().emailVerified()).isTrue();
        return email;
    }
}
