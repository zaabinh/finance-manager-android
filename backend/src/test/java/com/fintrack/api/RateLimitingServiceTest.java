package com.fintrack.api;

import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.service.auth.ClientMetadata;
import com.fintrack.api.service.auth.RateLimitingService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
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
    @Test void redisFailureOrMissingResultFailsClosedWith503() {
        assertUnavailable(() -> limiter.checkAndRecord("refresh", "ip", 20, Duration.ofMinutes(1)));
        when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("unavailable"));
        assertUnavailable(() -> limiter.checkLoginAllowed("user", client));
        when(redis.delete(anyString())).thenThrow(new RedisConnectionFailureException("unavailable"));
        assertUnavailable(() -> limiter.clearLoginFailures("user", client));
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