package com.fintrack.api.dto.request.auth;

import jakarta.validation.constraints.NotBlank;

/** rememberMe: "Ghi nhớ tôi"; null/absent keeps the long-lived session (older clients). */
public record LoginRequest(
        @NotBlank(message = "IDENTIFIER_REQUIRED")
        @jakarta.validation.constraints.Size(max = 254)
        String usernameOrEmail,
        @NotBlank(message = "PASSWORD_REQUIRED")
        @jakarta.validation.constraints.Size(max = 256)
        String password,
        Boolean rememberMe
) {
    public LoginRequest(String usernameOrEmail, String password) { this(usernameOrEmail, password, null); }
    @Override public String toString() { return "LoginRequest[REDACTED]"; }
}
