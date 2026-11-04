package com.fintrack.api;

import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.service.auth.ClientMetadata;
import com.fintrack.api.service.auth.RateLimitingService;
import com.fintrack.api.service.auth.TokenHash;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@Testcontainers
class RedisRateLimitingIT {
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    private static LettuceConnectionFactory connections;
    private static StringRedisTemplate redis;
    private String prefix;
    private RateLimitingService limiter;
    private final ClientMetadata client = new ClientMetadata("127.0.0.1", null);

    @BeforeAll static void connect() {
        connections = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connections.afterPropertiesSet();
        redis = new StringRedisTemplate(connections);
        try (var connection = connections.getConnection()) { assertThat(connection.ping()).isEqualTo("PONG"); }
    }
    @AfterAll static void disconnect() { if (connections != null) connections.destroy(); }
    @BeforeEach void uniqueNamespace() {
        prefix = "it-rate:" + UUID.randomUUID() + ':';
        limiter = new RateLimitingService(redis, prefix);
    }

    @Test void twoInstancesShareAtomicQuotaAcrossConcurrentRequests() throws Exception {
        var second = new RateLimitingService(redis, prefix);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(12)) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                var instance = i % 2 == 0 ? limiter : second;
                results.add(pool.submit(() -> {
                    start.await();
                    try { instance.checkAndRecord("refresh", "ip", 20, Duration.ofMinutes(1)); return true; }
                    catch (AppException ex) { assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_RATE_LIMITED); return false; }
                }));
            }
            start.countDown();
            int allowed = 0;
            for (var result : results) if (result.get(15, TimeUnit.SECONDS)) allowed++;
            assertThat(allowed).isEqualTo(20);
        }
        assertThat(redis.opsForValue().get(key("refresh", "ip"))).isEqualTo("20");
    }

    @Test void ttlExpiresWithoutBeingExtendedAndRecreatingServiceKeepsQuota() {
        String key = key("short", "ip");
        limiter.checkAndRecord("short", "ip", 2, Duration.ofMillis(1500));
        Long firstTtl = redis.getExpire(key, TimeUnit.MILLISECONDS);
        assertThat(firstTtl).isPositive();
        new RateLimitingService(redis, prefix).checkAndRecord("short", "ip", 2, Duration.ofMillis(1500));
        assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS)).isPositive().isLessThanOrEqualTo(firstTtl);
        assertRateLimited(() -> limiter.checkAndRecord("short", "ip", 2, Duration.ofMillis(1500)));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(redis.hasKey(key)).isFalse());
        assertThatCode(() -> limiter.checkAndRecord("short", "ip", 2, Duration.ofSeconds(1))).doesNotThrowAnyException();
    }

    @Test void loginSuccessClearsIdentifierButPreservesIpQuota() {
        for (int i = 0; i < 5; i++) limiter.recordLoginFailure(" USER ", client);
        assertRateLimited(() -> limiter.checkLoginAllowed("user", client));
        assertThatCode(() -> limiter.checkLoginAllowed("other", client)).doesNotThrowAnyException();
        limiter.clearLoginFailures("USER", client);
        assertThatCode(() -> limiter.checkLoginAllowed("user", client)).doesNotThrowAnyException();
        assertThat(redis.opsForValue().get(key("login-ip", client.ipAddress()))).isEqualTo("5");
        for (int i = 0; i < 45; i++) limiter.recordLoginFailure("other" + i, client);
        assertRateLimited(() -> limiter.checkLoginAllowed("fresh", client));
    }

    @Test void independentPrefixesAndCooldownDoNotInterfere() {
        limiter.checkAndRecord("cooldown", "email", 1, Duration.ofSeconds(60));
        assertRateLimited(() -> limiter.checkAndRecord("cooldown", "email", 1, Duration.ofSeconds(60)));
        assertThatCode(() -> new RateLimitingService(redis, prefix + "other:")
                .checkAndRecord("cooldown", "email", 1, Duration.ofSeconds(60))).doesNotThrowAnyException();
    }

    @Test void rejectedRedisWritesFailClosed() throws Exception {
        // A dedicated, short-lived Redis rejects all writes; shared Redis data/configuration is untouched.
        try (var readOnly = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379)
                .withCommand("redis-server", "--maxmemory", "1", "--maxmemory-policy", "noeviction")) {
            readOnly.start();
            var factory = new LettuceConnectionFactory(readOnly.getHost(), readOnly.getMappedPort(6379));
            factory.afterPropertiesSet();
            try {
                var unavailable = new RateLimitingService(new StringRedisTemplate(factory), prefix);
                assertThatThrownBy(() -> unavailable.checkAndRecord("refresh", "ip", 20, Duration.ofMinutes(1)))
                        .isInstanceOfSatisfying(AppException.class, ex -> {
                            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_RATE_LIMIT_UNAVAILABLE);
                            assertThat(ex.getStatus().value()).isEqualTo(503);
                        });
            } finally { factory.destroy(); }
        }
    }

    private String key(String scope, String identity) { return prefix + scope + ':' + TokenHash.sha256(identity); }
    private void assertRateLimited(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(AppException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_RATE_LIMITED));
    }
}
