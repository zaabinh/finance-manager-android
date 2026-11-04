package com.fintrack.api.dto.request.auth;
import jakarta.validation.constraints.*;
public record VerifyEmailRequest(@NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "[0-9]{4}") String otp) {
    @Override public String toString() { return "VerifyEmailRequest[REDACTED]"; }
}
