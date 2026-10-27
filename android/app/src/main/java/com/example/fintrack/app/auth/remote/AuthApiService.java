package com.example.fintrack.app.auth.remote;

import com.example.fintrack.app.core.network.ApiResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AuthApiService {

    @POST("api/v1/auth/login")
    Call<ApiResponse<LoginResponse>> login(@Body LoginRequest request);

    @POST("api/v1/auth/register")
    Call<ApiResponse<Void>> register(@Body RegisterRequest request);

    @POST("api/v1/auth/verify-email")
    Call<ApiResponse<Void>> verifyEmail(@Body VerifyEmailRequest request);

    @POST("api/v1/auth/resend-otp")
    Call<ApiResponse<Void>> resendOtp(@Body ResendOtpRequest request);

    @POST("api/v1/auth/forgot-password")
    Call<ApiResponse<Void>> forgotPassword(@Body ForgotPasswordRequest request);

    @POST("api/v1/auth/reset-password")
    Call<ApiResponse<Void>> resetPassword(@Body ResetPasswordRequest request);
}
