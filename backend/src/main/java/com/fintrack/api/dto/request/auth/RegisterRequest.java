package com.fintrack.api.dto.request.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "USERNAME_REQUIRED")
        String username,
        @Email(message = "EMAIL_INVALID")
        @NotBlank(message = "EMAIL_REQUIRED")
        String email,
        @NotBlank(message = "PASSWORD_REQUIRED")
        @Size(
                min = 8,
                max = 64,
                message = "PASSWORD_LENGTH_INVALID"
        )
        String password
) {
    
}
