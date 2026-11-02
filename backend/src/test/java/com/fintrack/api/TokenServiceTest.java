package com.fintrack.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fintrack.api.config.AuthProperties;
import com.fintrack.api.repository.*;
import com.fintrack.api.security.*;
import com.fintrack.api.service.auth.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

class TokenServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final Clock clock = Clock.systemUTC();
    private final UUID userId = UUID.randomUUID();
    private final AuthProperties properties = new AuthProperties("test-issuer", Duration.ofMinutes(15), Duration.ofDays(30),
            Duration.ofMinutes(10), null, null, Duration.ofSeconds(20));
    private TokenService tokens;
    private JwtEncoder encoder;
    @BeforeEach void setup() throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("test").generate();
        encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        var decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        tokens = new TokenService(new CustomUserDetailsService(users), encoder, decoder, properties, clock);
        var user = new UserRepository.UserRecord(userId, "user", "user@example.com", "hash", "ACTIVE", "USER", null, false, true);
        when(users.findById(userId)).thenReturn(Optional.of(user));
    }
    @Test void issuesRs256AccessTokenThatAuthenticates() {
        String access = tokens.createAccessToken(userId, "USER");
        assertThat(tokens.authenticate(access)).isPresent();
        assertThat(tokens.authenticate(access + "tampered")).isEmpty();
        assertThat(tokens.authenticate("not-a-jwt")).isEmpty();
    }
    @Test void rejectsExpiredWrongIssuerWrongSignatureAndInvalidSubject() throws Exception {
        var now = clock.instant();
        assertThat(tokens.authenticate(sign(encoder, "wrong", userId.toString(), now.plusSeconds(60)))).isEmpty();
        assertThat(tokens.authenticate(sign(encoder, properties.issuer(), userId.toString(), now.minusSeconds(60)))).isEmpty();
        assertThat(tokens.authenticate(sign(encoder, properties.issuer(), "not-a-uuid", now.plusSeconds(60)))).isEmpty();
        RSAKey otherKey = new RSAKeyGenerator(2048).generate();
        var otherEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(otherKey)));
        assertThat(tokens.authenticate(sign(otherEncoder, properties.issuer(), userId.toString(), now.plusSeconds(60)))).isEmpty();
    }
    private String sign(JwtEncoder signer, String issuer, String subject, Instant expiry) {
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(subject).id(UUID.randomUUID().toString())
                .issuedAt(clock.instant().minusSeconds(120)).expiresAt(expiry).claim("role", "USER").build();
        return signer.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }
}
