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

    @SerializedName("phone")
    private String phone;

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

    /** Backward-compatible name used by the profile settings screen. */
    public String getName() {
        return displayName;
    }

    public String getPhone() {
        return phone;
    }
}
