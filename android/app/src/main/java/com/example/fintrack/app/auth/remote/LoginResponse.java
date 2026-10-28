package com.example.fintrack.app.auth.remote;

import com.google.gson.annotations.SerializedName;

public class LoginResponse {

    @SerializedName("displayName")
    private String displayName;

    @SerializedName("onboardingCompleted")
    private boolean onboardingCompleted;

    @SerializedName("token")
    private TokenResponse token;

    public String getDisplayName() {
        return displayName;
    }

    public boolean isOnboardingCompleted() {
        return onboardingCompleted;
    }

    public TokenResponse getToken() {
        return token;
    }
}