package com.fintrack.api.dto.response.user;

public record UserSettingsResponse(
        UserProfileResponse profile,
        UserPreferencesResponse preferences,
        NotificationSettingsResponse notificationSettings
) {}
