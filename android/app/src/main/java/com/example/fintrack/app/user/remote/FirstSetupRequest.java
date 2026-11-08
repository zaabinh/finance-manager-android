package com.example.fintrack.app.user.remote;

import com.google.gson.annotations.SerializedName;

/** Body của POST api/v1/users/me/first-setup (wallets sẽ bổ sung khi có wallet API). */
public class FirstSetupRequest {
    @SerializedName("profile") private final UpdateProfileRequest profile;
    @SerializedName("notificationSettings") private final UpdateNotificationSettingsRequest notificationSettings;

    public FirstSetupRequest(UpdateProfileRequest profile, UpdateNotificationSettingsRequest notificationSettings) {
        this.profile = profile;
        this.notificationSettings = notificationSettings;
    }
}
