package com.fintrack.api.dto.request.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "IDENTIFIER_REQUIRED")
        String usernameOrEmail,
        @NotBlank(message = "PASSWORD_REQUIRED")
        String password

) {
}
