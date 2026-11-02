package com.fintrack.api;

import com.fintrack.api.exception.AppException;
import com.fintrack.api.service.auth.ClientMetadata;
import com.fintrack.api.service.auth.RateLimitingService;
import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RateLimitingServiceTest {
    @Test void loginWindowExpiresAndSuccessClearsOnlyTheIdentifier() {
        Clock clock = org.mockito.Mockito.mock(Clock.class);
        Instant now = Instant.parse("2026-11-02T00:00:00Z");
        org.mockito.Mockito.when(clock.instant()).thenReturn(now);
        var limiter = new RateLimitingService(clock);
        var client = new ClientMetadata("127.0.0.1", null);
        for (int i = 0; i < 5; i++) limiter.recordLoginFailure(" User ", client);
        assertThatThrownBy(() -> limiter.checkLoginAllowed("user", client)).isInstanceOf(AppException.class);
        limiter.clearLoginFailures("USER", client);
        assertThatCode(() -> limiter.checkLoginAllowed("user", client)).doesNotThrowAnyException();
        for (int i = 0; i < 5; i++) limiter.recordLoginFailure("user", client);
        org.mockito.Mockito.when(clock.instant()).thenReturn(now.plusSeconds(900));
        assertThatCode(() -> limiter.checkLoginAllowed("user", client)).doesNotThrowAnyException();
    }
    @Test void concurrentRequestsCannotExceedTheWindowLimit() throws Exception {
        var limiter = new RateLimitingService(Clock.systemUTC());
        try (var pool = Executors.newFixedThreadPool(8)) {
            var results = new java.util.ArrayList<Future<Boolean>>();
            for (int i = 0; i < 100; i++) results.add(pool.submit(() -> {
                try { limiter.checkAndRecord("refresh", "127.0.0.1", 20, Duration.ofMinutes(1)); return true; }
                catch (AppException exception) { return false; }
            }));
            int successes = 0;
            for (var result : results) if (result.get()) successes++;
            assertThat(successes).isEqualTo(20);
        }
    }
    @Test void capacityRejectsNewKeysInsteadOfEvictingActiveLimits() {
        var limiter = new RateLimitingService(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        for (int i = 0; i < 10_000; i++) limiter.checkAndRecord("test", "key" + i, 1, Duration.ofMinutes(1));
        assertThatThrownBy(() -> limiter.checkAndRecord("test", "new", 1, Duration.ofMinutes(1))).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> limiter.checkAndRecord("test", "key0", 1, Duration.ofMinutes(1))).isInstanceOf(AppException.class);
    }
}
