package com.fintrack.api.dto.request.auth;
import jakarta.validation.constraints.*;
public record ResendOtpRequest(@NotBlank @Email @Size(max = 254) String email) {
    @Override public String toString() { return "ResendOtpRequest[REDACTED]"; }
}
