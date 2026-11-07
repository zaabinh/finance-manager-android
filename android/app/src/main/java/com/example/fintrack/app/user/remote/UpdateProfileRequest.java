package com.example.fintrack.app.user.remote;

import com.google.gson.annotations.SerializedName;

/**
 * Lớp DTO đóng gói dữ liệu gửi lên Server khi người dùng cập nhật thông tin cá nhân.
 */
public class UpdateProfileRequest {

    @SerializedName("displayName")
    private final String displayName;

    @SerializedName("timeZone")
    private final String timeZone;

    public UpdateProfileRequest(String displayName, String timeZone) {
        this.displayName = displayName;
        this.timeZone = timeZone;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTimeZone() {
        return timeZone;
    }
}
