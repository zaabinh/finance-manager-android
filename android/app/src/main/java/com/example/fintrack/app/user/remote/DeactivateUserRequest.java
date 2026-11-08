package com.example.fintrack.app.user.remote;

import com.google.gson.annotations.SerializedName;

/** DTO gửi mật khẩu xác nhận khi vô hiệu hóa tài khoản (/api/v1/users/me) */
public class DeactivateUserRequest {

    @SerializedName("currentPassword")
    private String currentPassword;

    public DeactivateUserRequest(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }
}
