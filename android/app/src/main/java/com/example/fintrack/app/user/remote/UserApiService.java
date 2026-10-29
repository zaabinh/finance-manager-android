package com.example.fintrack.app.user.remote;

import com.example.fintrack.app.core.network.ApiResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PUT;

public interface UserApiService {

    @GET("api/v1/user/profile")
    Call<ApiResponse<UserProfileResponse>> getProfile();

    @PUT("api/v1/user/profile")
    Call<ApiResponse<UserProfileResponse>> updateProfile(@Body UserProfileResponse request);
}
