package com.fintrack.api.service.auth;

import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.ErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RateLimitingService {
    /** Returns 0 = denied, 1 = allowed without writing, 2 = allowed and the counter write succeeded. */
    private static final DefaultRedisScript<Long> WINDOW = new DefaultRedisScript<>("""
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            local limit = tonumber(ARGV[1])
            local ttl = tonumber(ARGV[2])
            local mode = ARGV[3]
            if mode == 'check' then
                if count >= limit then return 0 end
                return 1
            end
            if mode == 'consume' and count >= limit then return 0 end
            if count >= limit then return 1 end
            if count == 0 then
                redis.call('SET', KEYS[1], '1', 'PX', ttl)
            else
                redis.call('INCR', KEYS[1])
            end
            return 2
            """, Long.class);

    /** Caps fallback memory so distinct identifiers cannot exhaust the heap while Redis is down. */
    public static final int MAX_FALLBACK_KEYS = 100_000;
    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimitingService.class);

    private record Window(long count, long expiresAtNanos) {}

    private final StringRedisTemplate redis;
    private final String prefix;
    // shortcut: counts are per instance, so N instances allow up to N x limit during an outage; fine until we scale out.
    private final Map<String, Window> fallback = new ConcurrentHashMap<>();
    private final AtomicBoolean degraded = new AtomicBoolean();

    public RateLimitingService(StringRedisTemplate redis,
            @Value("${app.auth.rate-limit-key-prefix}") String prefix) {
        if (prefix == null || prefix.isBlank()) throw new IllegalArgumentException("Missing Redis key prefix");
        this.redis = redis;
        this.prefix = prefix;
    }

    public void checkLoginAllowed(String identifier, ClientMetadata client) {
        execute(loginKey(identifier, client), 5, Duration.ofMinutes(15), "check");
        execute(key("login-ip", client.ipAddress()), 50, Duration.ofMinutes(15), "check");
    }

    public void recordLoginFailure(String identifier, ClientMetadata client) {
        execute(loginKey(identifier, client), 5, Duration.ofMinutes(15), "bump");
        execute(key("login-ip", client.ipAddress()), 50, Duration.ofMinutes(15), "bump");
    }

    public void clearLoginFailures(String identifier, ClientMetadata client) {
        String key = loginKey(identifier, client);
        fallback.remove(key);
        // A successful DEL is not recovery: Redis still allows DEL while it rejects writes (e.g. out of memory).
        try {
            redis.delete(key);
        } catch (DataAccessException exception) {
            degrade(exception);
        }
    }

    public void checkAndRecord(String scope, String identifier, int limit, Duration duration) {
        execute(key(scope, identifier), limit, duration, "consume");
    }

    private void execute(String key, int limit, Duration duration, String mode) {
        if (limit < 1 || duration.toMillis() < 1) throw new IllegalArgumentException("Invalid rate limit");
        Long result = null;
        try {
            result = redis.execute(WINDOW, List.of(key), Integer.toString(limit),
                    Long.toString(duration.toMillis()), mode);
        } catch (DataAccessException exception) {
            degrade(exception);
        }
        if (result == null) degrade(null);
        // Only a confirmed counter write proves recovery; reads succeed even while Redis rejects writes.
        else if (result == 2L) recovered();
        // Until writes recover, the in-memory counters hold the failures Redis could not record.
        if (degraded.get() && (result == null || result != 0L))
            result = executeInMemory(key, limit, duration, mode) ? 1L : 0L;
        if (result == 0L) throw new AppException(HttpStatus.TOO_MANY_REQUESTS,
                ErrorCode.AUTH_RATE_LIMITED, "Too many requests; try again later");
    }

    /** Same fixed-window semantics as the Lua script, scoped to this JVM. */
    private boolean executeInMemory(String key, int limit, Duration duration, String mode) {
        long now = System.nanoTime();
        if (fallback.size() >= MAX_FALLBACK_KEYS) fallback.values().removeIf(window -> window.expiresAtNanos() - now <= 0);
        boolean[] allowed = {true};
        boolean[] full = {false};
        fallback.compute(key, (ignored, window) -> {
            if (window != null && window.expiresAtNanos() - now <= 0) window = null;
            long count = window == null ? 0 : window.count();
            if (count >= limit) { allowed[0] = "bump".equals(mode); return window; }
            if ("check".equals(mode)) return window;
            if (window == null) {
                if (fallback.size() >= MAX_FALLBACK_KEYS) { full[0] = true; return null; }
                return new Window(1, now + duration.toNanos());
            }
            return new Window(count + 1, window.expiresAtNanos());
        });
        // Fail closed rather than let a flood of new identifiers bypass limiting or exhaust memory.
        if (full[0]) throw unavailable();
        return allowed[0];
    }

    private void degrade(DataAccessException exception) {
        if (degraded.compareAndSet(false, true))
            LOGGER.warn("Redis rate limiting unavailable; using in-memory per-instance fallback", exception);
    }

    private void recovered() {
        if (degraded.compareAndSet(true, false)) {
            fallback.clear();
            LOGGER.info("Redis rate limiting recovered; in-memory fallback counts dropped");
        }
    }

    private String loginKey(String identifier, ClientMetadata client) {
        return key("login", identifier.trim().toLowerCase(Locale.ROOT) + ':' + client.ipAddress());
    }

    private String key(String scope, String identifier) {
        return prefix + scope + ':' + TokenHash.sha256(identifier.trim().toLowerCase(Locale.ROOT));
    }

    private AppException unavailable() {
        return new AppException(HttpStatus.SERVICE_UNAVAILABLE,
                ErrorCode.AUTH_RATE_LIMIT_UNAVAILABLE, "Authentication rate limiting is temporarily unavailable");
    }
}