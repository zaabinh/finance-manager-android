package com.fintrack.api.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class AuthenticationConfigTest {
    @TempDir Path directory;
    @Test void deploymentRequiresPersistentKeysAndDevCanGenerateEphemeralKeys() throws Exception {
        var config = new AuthenticationConfig();
        var properties = properties(null, null);
        assertThatThrownBy(() -> config.signingKey(properties, new MockEnvironment().withProperty("spring.profiles.active", "prod")))
                .isInstanceOf(IllegalArgumentException.class);
        var dev = new MockEnvironment(); dev.setActiveProfiles("dev");
        assertThat(config.signingKey(properties, dev).toRSAPublicKey().getModulus().bitLength()).isGreaterThanOrEqualTo(2048);
        dev.setActiveProfiles("dev", "prod");
        assertThatThrownBy(() -> config.signingKey(properties, dev)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void persistentKeyFilesSurviveRecreationAndMismatchedKeysFail() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var pair = generator.generateKeyPair();
        Path privateFile = directory.resolve("private.pem"); Path publicFile = directory.resolve("public.pem");
        Files.writeString(privateFile, pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
        Files.writeString(publicFile, pem("PUBLIC KEY", pair.getPublic().getEncoded()));
        var properties = properties(privateFile, publicFile);
        var env = new MockEnvironment(); env.setActiveProfiles("prod");
        assertThat(new AuthenticationConfig().signingKey(properties, env).toRSAPublicKey())
                .isEqualTo(new AuthenticationConfig().signingKey(properties, env).toRSAPublicKey());
        Files.writeString(publicFile, pem("PUBLIC KEY", generator.generateKeyPair().getPublic().getEncoded()));
        assertThatThrownBy(() -> new AuthenticationConfig().signingKey(properties, env)).isInstanceOf(IllegalArgumentException.class);
    }
    private AuthProperties properties(Path privateKey, Path publicKey) {
        return new AuthProperties("test", Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofMinutes(10),
                privateKey == null ? null : new FileSystemResource(privateKey), publicKey == null ? null : new FileSystemResource(publicKey), Duration.ofSeconds(20));
    }
    private String pem(String label, byte[] encoded) {
        return "-----BEGIN " + label + "-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded) + "\n-----END " + label + "-----\n";
    }
}
