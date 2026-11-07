package com.fintrack.api.dto.request.user;

import jakarta.validation.constraints.NotBlank;

public record DeactivateUserRequest(
    @NotBlank(message = "PASSWORD_REQUIRED")
    String currentPassword
) {
}
