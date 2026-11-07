package com.example.fintrack.app.user.remote;

import com.google.gson.annotations.SerializedName;

public class FirstSetupResponse {
    @SerializedName("profile") private UserProfileResponse profile;
    @SerializedName("notificationSettings") private NotificationSettingsResponse notificationSettings;
    @SerializedName("onboardingCompleted") private boolean onboardingCompleted;

    public UserProfileResponse getProfile() { return profile; }
    public NotificationSettingsResponse getNotificationSettings() { return notificationSettings; }
    public boolean isOnboardingCompleted() { return onboardingCompleted; }
}
