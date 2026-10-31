package com.fintrack.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.api.repository.RefreshSessionRepository;
import com.fintrack.api.service.TokenService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TokenServiceTest {
    @Test
    void issuedAccessTokenAuthenticatesAndTamperedTokenDoesNot() {
        var sessions = mock(RefreshSessionRepository.class);
        var tokens = new TokenService(sessions, new ObjectMapper(), "0123456789abcdef0123456789abcdef");

        var issued = tokens.issue(UUID.randomUUID(), "USER");

        assertThat(tokens.authenticate(issued.accessToken())).isPresent();
        assertThat(tokens.authenticate(issued.accessToken() + "tampered")).isEmpty();
        assertThat(tokens.authenticate("not-a-jwt")).isEmpty();
    }
}
