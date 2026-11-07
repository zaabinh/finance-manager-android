package com.example.fintrack.app.user.remote;

import com.example.fintrack.app.core.network.ApiResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;

public interface UserApiService {

    @GET("api/v1/users/me")
    Call<ApiResponse<UserProfileResponse>> getProfile();

    @PATCH("api/v1/users/me")
    Call<ApiResponse<UserProfileResponse>> updateProfile(@Body UpdateProfileRequest request);

    @GET("api/v1/users/me/settings")
    Call<ApiResponse<UserSettingsResponse>> getSettings();

    @GET("api/v1/users/me/notification-settings")
    Call<ApiResponse<NotificationSettingsResponse>> getNotificationSettings();

    @PATCH("api/v1/users/me/notification-settings")
    Call<ApiResponse<NotificationSettingsResponse>> updateNotificationSettings(
            @Body UpdateNotificationSettingsRequest request);
}
