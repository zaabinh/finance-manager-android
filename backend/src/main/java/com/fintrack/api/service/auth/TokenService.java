package com.fintrack.api.service.auth;

import com.fintrack.api.config.AuthProperties;
import com.fintrack.api.entity.enums.UserRole;
import com.fintrack.api.security.AuthenticatedUser;
import com.fintrack.api.security.CustomUserDetailsService;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

/** Stateless access tokens (RS256 JWT). Refresh tokens live in {@link RefreshTokenRotatingService}. */
@Service
@Profile("!test")
public class TokenService {
    private final CustomUserDetailsService details;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final AuthProperties properties;
    private final Clock clock;
    public TokenService(CustomUserDetailsService details, JwtEncoder encoder, JwtDecoder decoder,
            AuthProperties properties, Clock clock) {
        this.details = details; this.encoder = encoder; this.decoder = decoder; this.properties = properties; this.clock = clock;
    }
    public Optional<Authentication> authenticate(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            if (jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(clock.instant())
                    || jwt.getIssuedAt() == null || jwt.getIssuedAt().isAfter(clock.instant()) || jwt.getId() == null) return Optional.empty();
            AuthenticatedUser user = details.loadById(UUID.fromString(jwt.getSubject()));
            if (!user.isEnabled() || !user.isAccountNonLocked() || !user.user().emailVerified()) return Optional.empty();
            return Optional.of(UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
        } catch (org.springframework.security.core.AuthenticationException | JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
    public String createAccessToken(UUID userId, UserRole role) {
        Instant now = clock.instant();
        var claims = JwtClaimsSet.builder().issuer(properties.issuer()).subject(userId.toString())
                .issuedAt(now).expiresAt(now.plus(properties.accessTokenTtl())).id(UUID.randomUUID().toString())
                .claim("role", role.name()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }
}
