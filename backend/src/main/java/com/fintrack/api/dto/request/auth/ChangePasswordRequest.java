package com.fintrack.api.dto.request.auth;
import jakarta.validation.constraints.*;
public record ChangePasswordRequest(@NotBlank @Size(max = 128) String currentPassword,
        @NotBlank @ValidPassword String newPassword) {
    @Override public String toString() { return "ChangePasswordRequest[REDACTED]"; }
}
