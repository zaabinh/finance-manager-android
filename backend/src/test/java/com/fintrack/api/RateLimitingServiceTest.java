package com.fintrack.api;

import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.service.auth.ClientMetadata;
import com.fintrack.api.service.auth.RateLimitingService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RateLimitingServiceTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RateLimitingService limiter = new RateLimitingService(redis, "test:");
    private final ClientMetadata client = new ClientMetadata("127.0.0.1", null);

    @Test void admitsOnlyWhenRedisGrantsQuota() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString())).thenReturn(1L);
        assertThatCode(() -> limiter.checkAndRecord("refresh", "ip", 20, Duration.ofMinutes(1))).doesNotThrowAnyException();
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString())).thenReturn(0L);
        assertThatThrownBy(() -> limiter.checkAndRecord("refresh", "ip", 20, Duration.ofMinutes(1)))
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_RATE_LIMITED);
                    assertThat(ex.getStatus().value()).isEqualTo(429);
                });
    }
    @Test void missingRedisResultFallsBackToMemoryWithSameLimit() {
        // Unstubbed mock returns null, which is treated as a Redis failure.
        for (int i = 0; i < 3; i++) limiter.checkAndRecord("refresh", "ip", 3, Duration.ofMinutes(1));
        assertRateLimited(() -> limiter.checkAndRecord("refresh", "ip", 3, Duration.ofMinutes(1)));
        assertThatCode(() -> limiter.checkAndRecord("refresh", "other-ip", 3, Duration.ofMinutes(1))).doesNotThrowAnyException();
    }
    @Test void redisDownLoginLockoutWorksInMemoryAndSuccessClearsIt() {
        redisDown();
        when(redis.delete(anyString())).thenThrow(new RedisConnectionFailureException("unavailable"));
        assertThatCode(() -> limiter.checkLoginAllowed("user", client)).doesNotThrowAnyException();
        for (int i = 0; i < 6; i++) limiter.recordLoginFailure("user", client); // bump past limit never throws
        assertRateLimited(() -> limiter.checkLoginAllowed("USER ", client));
        assertThatCode(() -> limiter.clearLoginFailures("user", client)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.checkLoginAllowed("user", client)).doesNotThrowAnyException();
    }
    @Test void fallbackWindowExpires() throws InterruptedException {
        redisDown();
        limiter.checkAndRecord("otp", "ip", 1, Duration.ofMillis(50));
        assertRateLimited(() -> limiter.checkAndRecord("otp", "ip", 1, Duration.ofMillis(50)));
        Thread.sleep(80);
        assertThatCode(() -> limiter.checkAndRecord("otp", "ip", 1, Duration.ofMillis(50))).doesNotThrowAnyException();
    }
    @Test void redisRecoveryDropsInMemoryCounts() {
        redisDown();
        limiter.checkAndRecord("refresh", "ip", 1, Duration.ofMinutes(1));
        assertRateLimited(() -> limiter.checkAndRecord("refresh", "ip", 1, Duration.ofMinutes(1)));
        reset(redis);
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString())).thenReturn(2L);
        limiter.checkAndRecord("refresh", "ip", 1, Duration.ofMinutes(1)); // confirmed Redis write: recovered
        redisDown();
        assertThatCode(() -> limiter.checkAndRecord("refresh", "ip", 1, Duration.ofMinutes(1))).doesNotThrowAnyException();
    }
    @Test void successfulReadsWithFailedWritesKeepEnforcingFallbackCounters() {
        // Redis answers reads (check) but rejects counter writes (bump/consume), e.g. out of memory.
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), eq("check"))).thenReturn(1L);
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), eq("bump")))
                .thenThrow(new RedisSystemException("OOM", new RuntimeException("OOM command not allowed")));
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), eq("consume")))
                .thenThrow(new RedisSystemException("OOM", new RuntimeException("OOM command not allowed")));
        for (int i = 0; i < 5; i++) {
            limiter.checkLoginAllowed("user", client);
            limiter.recordLoginFailure("user", client);
            limiter.checkAndRecord("login-request", "ip", 10, Duration.ofMinutes(1));
        }
        assertRateLimited(() -> limiter.checkLoginAllowed("user", client)); // lockout accumulated despite Redis reads
        for (int i = 5; i < 10; i++) limiter.checkAndRecord("login-request", "ip", 10, Duration.ofMinutes(1));
        assertRateLimited(() -> limiter.checkAndRecord("login-request", "ip", 10, Duration.ofMinutes(1)));
        // A non-writing Redis answer (bump at limit returns 1) is not recovery either.
        doReturn(1L).when(redis).execute(any(RedisScript.class), anyList(), anyString(), anyString(), eq("bump"));
        limiter.recordLoginFailure("user", client);
        assertRateLimited(() -> limiter.checkLoginAllowed("user", client));
    }
    @Test void fullFallbackRejectsNewKeysButKeepsCountingKnownOnes() {
        redisDown();
        limiter.checkAndRecord("refresh", "known", 5, Duration.ofMinutes(1));
        for (int i = 1; i < RateLimitingService.MAX_FALLBACK_KEYS; i++)
            limiter.checkAndRecord("refresh", "ip-" + i, 5, Duration.ofMinutes(1));
        assertUnavailable(() -> limiter.checkAndRecord("refresh", "new-ip", 5, Duration.ofMinutes(1)));
        assertThatCode(() -> limiter.checkAndRecord("refresh", "known", 5, Duration.ofMinutes(1))).doesNotThrowAnyException();
    }
    private void redisDown() {
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("unavailable"));
    }
    private void assertRateLimited(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(AppException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_RATE_LIMITED));
    }
    @Test void successfulLoginDeletesOnlyItsNormalizedIdentifierKey() {
        limiter.clearLoginFailures(" USER ", client);
        verify(redis).delete("test:login:" + com.fintrack.api.service.auth.TokenHash.sha256("user:127.0.0.1"));
        verifyNoMoreInteractions(redis);
    }
    @Test void invalidQuotaNeverCallsRedis() {
        assertThatThrownBy(() -> limiter.checkAndRecord("refresh", "ip", 0, Duration.ofMinutes(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> limiter.checkAndRecord("refresh", "ip", 20, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(redis);
    }
    private void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(AppException.class, ex -> {
            assertThat(ex.getStatus().value()).isEqualTo(503);
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_RATE_LIMIT_UNAVAILABLE);
        });
    }
}