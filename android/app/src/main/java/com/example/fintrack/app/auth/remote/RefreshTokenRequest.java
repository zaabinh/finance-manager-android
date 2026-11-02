package com.example.fintrack.app.auth.remote;

import com.google.gson.annotations.SerializedName;

public class RefreshTokenRequest {

    @SerializedName("refreshToken")
    private final String refreshToken;

    public RefreshTokenRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }
}
