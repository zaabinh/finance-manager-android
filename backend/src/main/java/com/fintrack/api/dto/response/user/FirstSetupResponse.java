package com.fintrack.api.dto.response.user;

public record FirstSetupResponse(
        UserProfileResponse profile,
        NotificationSettingsResponse notificationSettings,
        boolean onboardingCompleted
) {}
