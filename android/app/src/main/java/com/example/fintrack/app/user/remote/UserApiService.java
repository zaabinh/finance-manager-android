package com.example.fintrack.app.user.remote;

import com.example.fintrack.app.core.network.ApiResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;

public interface UserApiService {

    @GET("api/v1/users/me")
    Call<ApiResponse<UserProfileResponse>> getProfile();

    @PATCH("api/v1/users/me")
    Call<ApiResponse<UserProfileResponse>> updateProfile(@Body UpdateProfileRequest request);

    /** API Xóa vĩnh viễn tài khoản người dùng */
    @DELETE("api/v1/user/profile")
    Call<ApiResponse<Void>> deleteAccount();
  
    @GET("api/v1/users/me/settings")
    Call<ApiResponse<UserSettingsResponse>> getSettings();

    @GET("api/v1/users/me/notification-settings")
    Call<ApiResponse<NotificationSettingsResponse>> getNotificationSettings();

    @PATCH("api/v1/users/me/notification-settings")
    Call<ApiResponse<NotificationSettingsResponse>> updateNotificationSettings(
            @Body UpdateNotificationSettingsRequest request);
}
