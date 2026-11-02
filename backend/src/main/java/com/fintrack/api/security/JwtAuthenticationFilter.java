package com.fintrack.api.security;

import com.fintrack.api.service.auth.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Profile("!test")
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final TokenService tokens;
    private final SecurityErrorWriter errors;
    private final org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver bearerTokens =
            new org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver();

    public JwtAuthenticationFilter(TokenService tokens, SecurityErrorWriter errors) { this.tokens = tokens; this.errors = errors; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token;
        try { token = bearerTokens.resolve(request); }
        catch (org.springframework.security.oauth2.core.OAuth2AuthenticationException exception) {
            errors.write(request, response, 401, com.fintrack.api.exception.ErrorCode.AUTH_ACCESS_TOKEN_INVALID, "Invalid access token");
            return;
        }
        if (token != null) {
            var authentication = tokens.authenticate(token);
            if (authentication.isEmpty()) {
                errors.write(request, response, 401, com.fintrack.api.exception.ErrorCode.AUTH_ACCESS_TOKEN_INVALID, "Invalid access token");
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(authentication.get());
        }
        chain.doFilter(request, response);
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().startsWith("/api/v1/auth/");
    }
}
