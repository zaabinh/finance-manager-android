package com.example.fintrack.app.auth.remote;

import com.google.gson.annotations.SerializedName;

public class LoginResponse {
    @SerializedName("onboardingCompleted")
    private boolean onboardingCompleted;

    @SerializedName("token")
    private TokenResponse token;

    public boolean isOnboardingCompleted() {
        return onboardingCompleted;
    }

    public TokenResponse getToken() {
        return token;
    }
}
