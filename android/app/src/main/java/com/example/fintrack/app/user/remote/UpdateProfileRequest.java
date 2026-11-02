package com.example.fintrack.app.user.remote;

import com.google.gson.annotations.SerializedName;

public class UpdateProfileRequest {

    @SerializedName("name")
    private String name;

    @SerializedName("phone")
    private String phone;

    public UpdateProfileRequest(String name, String phone) {
        this.name = name;
        this.phone = phone;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }
}
