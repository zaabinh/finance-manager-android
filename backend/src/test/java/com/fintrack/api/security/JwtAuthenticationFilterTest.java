package com.fintrack.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.api.service.auth.TokenService;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {
    private final TokenService tokens = mock(TokenService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokens,
            new SecurityErrorWriter(new ObjectMapper().findAndRegisterModules()));

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void changePasswordAuthenticatesBearerTokenInsteadOfSkippingAuthRoutes() throws Exception {
        var authentication = UsernamePasswordAuthenticationToken.authenticated("user", null, java.util.List.of());
        when(tokens.authenticate("valid")).thenReturn(Optional.of(authentication));
        filter.doFilter(request("/api/v1/auth/change-password", "valid"), new MockHttpServletResponse(),
                (request, response) -> assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(authentication));
        verify(tokens).authenticate("valid");
    }

    @Test void expiredBearerDoesNotBlockPublicAuthEndpoints() throws Exception {
        for (String endpoint : new String[]{"register", "login", "verify-email", "resend-verification",
                "forgot-password", "reset-password", "refresh", "logout"}) {
            var response = new MockHttpServletResponse();
            filter.doFilter(request("/api/v1/auth/" + endpoint, "expired"), response,
                    (req, res) -> res.getWriter().write("public"));
            assertThat(response.getContentAsString()).isEqualTo("public");
        }
        verifyNoInteractions(tokens);
    }

    @Test void invalidBearerOnChangePasswordReturnsJson401() throws Exception {
        when(tokens.authenticate("invalid")).thenReturn(Optional.empty());
        var response = new MockHttpServletResponse();
        filter.doFilter(request("/api/v1/auth/change-password", "invalid"), response,
                (req, res) -> fail("Protected request must not proceed"));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("AUTH_ACCESS_TOKEN_INVALID");
    }

    private MockHttpServletRequest request(String path, String token) {
        var request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
