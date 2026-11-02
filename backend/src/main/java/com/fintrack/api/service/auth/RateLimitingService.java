package com.fintrack.api.service.auth;

import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Bounded, process-local fixed windows. Synchronization also makes the capacity check atomic. */
@Service
public class RateLimitingService {
    private static final int MAX_KEYS = 10_000;
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    public RateLimitingService(Clock clock) { this.clock = clock; }

    public synchronized void checkLoginAllowed(String identifier, ClientMetadata client) {
        check(loginKey(identifier, client), 5);
        check("login-ip:" + client.ipAddress(), 50);
    }

    public synchronized void recordLoginFailure(String identifier, ClientMetadata client) {
        increment(loginKey(identifier, client), Duration.ofMinutes(15));
        increment("login-ip:" + client.ipAddress(), Duration.ofMinutes(15));
    }

    public synchronized void clearLoginFailures(String identifier, ClientMetadata client) {
        windows.remove(loginKey(identifier, client));
    }

    public synchronized void checkAndRecord(String scope, String identifier, int limit, Duration duration) {
        String key = scope + ':' + TokenHash.sha256(identifier.trim().toLowerCase(Locale.ROOT));
        check(key, limit);
        increment(key, duration);
    }

    private String loginKey(String identifier, ClientMetadata client) {
        return "login:" + TokenHash.sha256(identifier.trim().toLowerCase(Locale.ROOT)) + ':' + client.ipAddress();
    }

    private void check(String key, int limit) {
        Window window = windows.get(key);
        if (window != null && window.expiresAt().isAfter(clock.instant()) && window.count() >= limit) reject();
    }

    private void increment(String key, Duration duration) {
        Instant now = clock.instant();
        windows.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        Window current = windows.get(key);
        if (current == null && windows.size() >= MAX_KEYS) reject();
        windows.put(key, current == null ? new Window(1, now.plus(duration))
                : new Window(current.count() + 1, current.expiresAt()));
    }

    private void reject() {
        throw new AppException(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.AUTH_RATE_LIMITED, "Too many requests; try again later");
    }

    private record Window(int count, Instant expiresAt) { }
}
