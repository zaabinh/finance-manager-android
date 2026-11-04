package com.fintrack.api.dto.response.auth;

public record LoginResponse(
        String displayName,
        boolean onboardingCompleted,
        TokenResponse token
) {
    public static LoginResponse of(String displayName, String username, boolean onboardingCompleted,
            TokenResponse token) {
        String resolvedDisplayName = displayName == null || displayName.isBlank() ? username : displayName;
        return new LoginResponse(resolvedDisplayName, onboardingCompleted, token);
    }
}
