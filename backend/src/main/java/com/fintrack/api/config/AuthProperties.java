package com.fintrack.api.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

@ConfigurationProperties("app.auth")
public record AuthProperties(
        @DefaultValue("fintrack-api") String issuer,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("30d") Duration refreshTokenTtl,
        @DefaultValue("10m") Duration otpTtl,
        Resource privateKey, Resource publicKey,
        @DefaultValue("20s") Duration refreshReuseGracePeriod) {
    public AuthProperties {
        if (refreshReuseGracePeriod == null) refreshReuseGracePeriod = Duration.ofSeconds(20);
        if (issuer == null || issuer.isBlank() || accessTokenTtl.toSeconds() < 1
                || refreshTokenTtl.toSeconds() < 1 || otpTtl.toSeconds() < 1
                // A long grace period turns a stolen-and-replayed token into a working one; keep it short.
                || refreshReuseGracePeriod.isNegative() || refreshReuseGracePeriod.compareTo(Duration.ofMinutes(1)) > 0) {
            throw new IllegalArgumentException("Invalid authentication configuration");
        }
    }
}
