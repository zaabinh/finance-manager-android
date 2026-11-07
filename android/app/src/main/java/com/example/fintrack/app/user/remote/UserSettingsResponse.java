package com.example.fintrack.app.user.remote;

import com.google.gson.annotations.SerializedName;

public class UserSettingsResponse {
    @SerializedName("profile") private UserProfileResponse profile;
    @SerializedName("preferences") private UserPreferencesResponse preferences;
    @SerializedName("notificationSettings") private NotificationSettingsResponse notificationSettings;

    public UserProfileResponse getProfile() { return profile; }
    public UserPreferencesResponse getPreferences() { return preferences; }
    public NotificationSettingsResponse getNotificationSettings() { return notificationSettings; }
}
