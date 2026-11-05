package com.example.fintrack.app.user.remote;

import com.example.fintrack.app.core.network.ApiResponse;

import retrofit2.Call;
import retrofit2.http.GET;

public interface UserApiService {

    @GET("api/v1/users/me")
    Call<ApiResponse<UserProfileResponse>> getProfile();
}
