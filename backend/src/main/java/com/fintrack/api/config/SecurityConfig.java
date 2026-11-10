package com.fintrack.api.config;

import com.fintrack.api.security.SecurityErrorWriter;
import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.fintrack.api.security.JwtAuthenticationFilter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
    @Bean
    java.time.Clock clock() { return java.time.Clock.systemUTC(); }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment,
            SecurityErrorWriter errorWriter, CorsConfigurationSource corsConfigurationSource,
            ObjectProvider<JwtAuthenticationFilter> jwtFilter) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                errorWriter.write(request, response, 401, "Authentication required"))
                        .accessDeniedHandler((request, response, exception) ->
                                errorWriter.write(request, response, 403, "Access denied")))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers("/actuator/health", "/actuator/health/liveness",
                            "/actuator/health/readiness").permitAll();
                    // Images referenced by outgoing emails (static/email/**).
                    authorize.requestMatchers(org.springframework.http.HttpMethod.GET, "/email/**").permitAll();
                    authorize.requestMatchers(org.springframework.http.HttpMethod.POST,
                            "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/verify-email",
                            "/api/v1/auth/resend-verification", "/api/v1/auth/forgot-password",
                            "/api/v1/auth/reset-password", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll();
                    if (environment.acceptsProfiles(Profiles.of("dev & !prod"))) {
                        authorize.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs",
                                "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll();
                    }
                    authorize.anyRequest().authenticated();
                });
        jwtFilter.ifAvailable(filter -> http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins:}") List<String> origins,
            @Value("${app.auth.refresh-cookie:false}") boolean refreshCookie) {
        List<String> allowedOrigins = origins.stream().map(String::trim).filter(origin -> !origin.isEmpty()).toList();
        allowedOrigins.forEach(origin -> {
            URI uri = URI.create(origin);
            if (origin.contains("*") || uri.getHost() == null || uri.getUserInfo() != null
                    || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("CORS origins must be explicit HTTP(S) origins");
            }
        });
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        // Credentials only for the refresh cookie; safe because origins above are explicit (never "*").
        configuration.setAllowCredentials(refreshCookie);
        configuration.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
