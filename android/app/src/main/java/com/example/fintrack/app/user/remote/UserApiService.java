package com.example.fintrack.app.user.remote;

import com.example.fintrack.app.core.network.ApiResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.PATCH;
import retrofit2.http.POST;

/**
 * Interface định nghĩa các API endpoint liên quan đến Hồ sơ người dùng (User Profile)
 * khớp 100% với Backend Spring Boot (@RequestMapping("/api/v1/users/me"))
 */
public interface UserApiService {

    /** API Lấy thông tin hồ sơ cá nhân của người dùng hiện tại */
    @GET("api/v1/users/me")
    Call<ApiResponse<UserProfileResponse>> getProfile();

    /** API Cập nhật thông tin hồ sơ cá nhân */
    @PATCH("api/v1/users/me")
    Call<ApiResponse<UserProfileResponse>> updateProfile(@Body UpdateProfileRequest request);

    /** API Vô hiệu hóa/Xóa tài khoản người dùng với xác nhận mật khẩu hiện tại */
    @HTTP(method = "DELETE", path = "api/v1/users/me", hasBody = true)
    Call<ApiResponse<Void>> deactivateAccount(@Body DeactivateUserRequest request);

    @GET("api/v1/users/me/settings")
    Call<ApiResponse<UserSettingsResponse>> getSettings();

    @GET("api/v1/users/me/notification-settings")
    Call<ApiResponse<NotificationSettingsResponse>> getNotificationSettings();

    @PATCH("api/v1/users/me/notification-settings")
    Call<ApiResponse<NotificationSettingsResponse>> updateNotificationSettings(@Body UpdateNotificationSettingsRequest request);

    @POST("api/v1/users/me/first-setup")
    Call<ApiResponse<FirstSetupResponse>> completeFirstSetup(@Body FirstSetupRequest request);
}
