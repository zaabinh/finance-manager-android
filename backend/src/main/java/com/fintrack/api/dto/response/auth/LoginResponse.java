package com.fintrack.api.dto.response.auth;

public record LoginResponse(
        boolean onboardingCompleted,
        TokenResponse token
) {}
