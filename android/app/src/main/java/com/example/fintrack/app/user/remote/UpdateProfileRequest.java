package com.example.fintrack.app.user.remote;

import com.google.gson.annotations.SerializedName;

/**
 * Lớp DTO đóng gói dữ liệu gửi lên Server khi người dùng cập nhật thông tin cá nhân.
 */
public class UpdateProfileRequest {

    @SerializedName("name")
    private String name; // Họ và tên mới

    @SerializedName("phone")
    private String phone; // Số điện thoại mới

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
