package com.fintrack.api.dto.request.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "IDENTIFIER_REQUIRED")
        @jakarta.validation.constraints.Size(max = 254)
        String usernameOrEmail,
        @NotBlank(message = "PASSWORD_REQUIRED")
        @jakarta.validation.constraints.Size(max = 256)
        String password

) {
    @Override public String toString() { return "LoginRequest[REDACTED]"; }
}
