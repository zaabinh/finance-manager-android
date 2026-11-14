package com.example.fintrack.app.user.remote;

import com.google.gson.annotations.SerializedName;

public class UserProfileResponse {

    @SerializedName("id")
    private String id;

    @SerializedName("username")
    private String username;

    @SerializedName("email")
    private String email;

    @SerializedName("displayName")
    private String displayName;

    @SerializedName("currencyCode")
    private String currencyCode;

    @SerializedName("timeZone")
    private String timeZone;

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getTimeZone() {
        return timeZone;
    }
}
