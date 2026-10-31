package com.fintrack.api.service;

import com.fintrack.api.dto.response.auth.TokenResponse;
import com.fintrack.api.repository.RefreshSessionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;

@Service
@Profile("!test")
public class TokenService {
    private static final long ACCESS_SECONDS = 900;
    private static final long REFRESH_SECONDS = 2_592_000;
    private final RefreshSessionRepository sessions;
    private final byte[] signingKey;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    public TokenService(RefreshSessionRepository sessions, ObjectMapper objectMapper,
            @Value("${app.auth.jwt-secret}") String secret) {
        this.sessions = sessions;
        this.objectMapper = objectMapper;
        this.signingKey = secret.getBytes(StandardCharsets.UTF_8);
        if (signingKey.length < 32) throw new IllegalArgumentException("JWT signing secret must contain at least 32 bytes");
    }

    public Optional<Authentication> authenticate(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3 || !MessageDigest.isEqual(
                    sign(parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII),
                    parts[2].getBytes(StandardCharsets.US_ASCII))) return Optional.empty();
            var payload = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            long expires = payload.path("exp").asLong(0);
            String subject = payload.path("sub").asText("");
            String role = payload.path("role").asText("USER");
            if (subject.isBlank() || expires <= Instant.now().getEpochSecond()
                    || !role.matches("USER|ADMIN")) return Optional.empty();
            return Optional.of(new UsernamePasswordAuthenticationToken(subject, null,
                    java.util.List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    public TokenResponse issue(UUID userId, String role) {
        Instant now = Instant.now();
        String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = encode("{\"sub\":\"" + userId + "\",\"role\":\"" + role
                + "\",\"iat\":" + now.getEpochSecond() + ",\"exp\":" + now.plusSeconds(ACCESS_SECONDS).getEpochSecond() + "}");
        String access = header + "." + payload + "." + sign(header + "." + payload);
        byte[] raw = new byte[32]; random.nextBytes(raw);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        sessions.create(userId, sha256(refresh), now.plusSeconds(REFRESH_SECONDS));
        return new TokenResponse(access, refresh, "Bearer", ACCESS_SECONDS);
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign access token", exception);
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
