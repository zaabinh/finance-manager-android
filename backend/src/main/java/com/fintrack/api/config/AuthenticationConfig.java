package com.fintrack.api.config;

import com.fintrack.api.security.CustomUserDetailsService;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;

@Configuration(proxyBeanMethods = false)
@Profile("!test")
@EnableConfigurationProperties(AuthProperties.class)
public class AuthenticationConfig {
    @Bean
    org.springframework.boot.web.servlet.FilterRegistrationBean<com.fintrack.api.security.JwtAuthenticationFilter> jwtFilterRegistration(
            com.fintrack.api.security.JwtAuthenticationFilter filter) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
    @Bean
    RSAKey signingKey(AuthProperties properties, Environment environment) throws Exception {
        RSAPublicKey publicKey;
        RSAPrivateKey privateKey;
        if (properties.privateKey() != null && properties.publicKey() != null) {
            try (var privateStream = properties.privateKey().getInputStream();
                 var publicStream = properties.publicKey().getInputStream()) {
                privateKey = RsaKeyConverters.pkcs8().convert(privateStream);
                publicKey = RsaKeyConverters.x509().convert(publicStream);
            }
            if (!privateKey.getModulus().equals(publicKey.getModulus())) {
                throw new IllegalArgumentException("JWT keys do not match");
            }
        } else if (properties.privateKey() == null && properties.publicKey() == null
                && environment.acceptsProfiles(Profiles.of("dev & !prod & !staging"))) {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var pair = generator.generateKeyPair();
            publicKey = (RSAPublicKey) pair.getPublic();
            privateKey = (RSAPrivateKey) pair.getPrivate();
        } else {
            throw new IllegalArgumentException("Configure AUTH_JWT_PRIVATE_KEY and AUTH_JWT_PUBLIC_KEY resources");
        }
        if (publicKey.getModulus().bitLength() < 2048) throw new IllegalArgumentException("RSA key must be at least 2048 bits");
        return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("fintrack-rsa").build();
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey key) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    @Bean
    JwtDecoder jwtDecoder(RSAKey key, AuthProperties properties, Clock clock) throws Exception {
        var decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        var timestamp = new JwtTimestampValidator(java.time.Duration.ZERO);
        timestamp.setClock(clock);
        decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                timestamp, new JwtIssuerValidator(properties.issuer())));
        return decoder;
    }

    @Bean
    AuthenticationManager authenticationManager(CustomUserDetailsService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
}
