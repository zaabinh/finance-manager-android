package com.example.fintrack.app.user.remote;

import com.google.gson.annotations.SerializedName;

/**
 * Lớp DTO nhận dữ liệu phản hồi thông tin Hồ sơ cá nhân từ Server.
 */
public class UserProfileResponse {

    @SerializedName("userId")
    private String userId; // Mã định danh duy nhất của người dùng

    @SerializedName("email")
    private String email; // Địa chỉ email đăng ký tài khoản

    @SerializedName("name")
    private String name; // Họ và tên hiển thị

    @SerializedName("phone")
    private String phone; // Số điện thoại liên hệ

    public String getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }
}
