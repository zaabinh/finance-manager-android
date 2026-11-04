package com.fintrack.api.service.auth;

import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.ErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RateLimitingService {
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
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final String prefix;

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
        try {
            redis.delete(loginKey(identifier, client));
        } catch (DataAccessException exception) {
            throw unavailable();
        }
    }

    public void checkAndRecord(String scope, String identifier, int limit, Duration duration) {
        execute(key(scope, identifier), limit, duration, "consume");
    }

    private void execute(String key, int limit, Duration duration, String mode) {
        if (limit < 1 || duration.toMillis() < 1) throw new IllegalArgumentException("Invalid rate limit");
        Long result;
        try {
            result = redis.execute(WINDOW, List.of(key), Integer.toString(limit),
                    Long.toString(duration.toMillis()), mode);
        } catch (DataAccessException exception) {
            throw unavailable();
        }
        if (result == null) throw unavailable();
        if (result == 0L) throw new AppException(HttpStatus.TOO_MANY_REQUESTS,
                ErrorCode.AUTH_RATE_LIMITED, "Too many requests; try again later");
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