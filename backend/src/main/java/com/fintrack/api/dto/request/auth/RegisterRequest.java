package com.fintrack.api.dto.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "USERNAME_REQUIRED")
        @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z0-9._-]{3,50}", message = "USERNAME_INVALID")
        String username,
        @Email(message = "EMAIL_INVALID")
        @NotBlank(message = "EMAIL_REQUIRED")
        @Size(max = 254)
        String email,
        @NotBlank(message = "PASSWORD_REQUIRED")
        @ValidPassword
        String password
) {
    @Override public String toString() { return "RegisterRequest[REDACTED]"; }
    public RegisterRequest {
        username = username == null ? null : username.trim();
        email = email == null ? null : email.trim();
    }
}
