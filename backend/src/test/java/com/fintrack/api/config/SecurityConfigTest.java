package com.fintrack.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SecurityConfigTest {

    @Test
    void corsRejectsWildcardAndInvalidOrigins() {
        var config = new SecurityConfig();
        for (String origin : List.of("*", "https://*.example.com", "https://example.com/path", "file:///tmp")) {
            assertThrows(IllegalArgumentException.class,
                    () -> config.corsConfigurationSource(List.of(origin)));
        }
    }

    @Test
    void corsDefaultsToNoAllowedOrigins() {
        var source = new SecurityConfig().corsConfigurationSource(List.of());
        var configuration = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/wallets"));
        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).isEmpty();
        assertThat(configuration.getAllowCredentials()).isFalse();
    }
}
