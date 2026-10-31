package com.fintrack.api.security;

import com.fintrack.api.service.TokenService;
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

    public JwtAuthenticationFilter(TokenService tokens) { this.tokens = tokens; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            tokens.authenticate(header.substring(7)).ifPresent(authentication ->
                    SecurityContextHolder.getContext().setAuthentication(authentication));
        }
        chain.doFilter(request, response);
    }
}
