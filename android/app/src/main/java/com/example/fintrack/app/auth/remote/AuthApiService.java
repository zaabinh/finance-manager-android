package com.example.fintrack.app.auth.remote;

import com.example.fintrack.app.core.network.ApiResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * Interface định nghĩa các API endpoint liên quan đến xác thực (Auth)
 * sử dụng thư viện Retrofit.
 */
public interface AuthApiService {

    /** API Đăng nhập tài khoản */
    @POST("api/v1/auth/login")
    Call<ApiResponse<LoginResponse>> login(@Body LoginRequest request);

    /** API Đăng ký tài khoản mới */
    @POST("api/v1/auth/register")
    Call<ApiResponse<Void>> register(@Body RegisterRequest request);

    /** API Xác thực email bằng mã OTP */
    @POST("api/v1/auth/verify-email")
    Call<ApiResponse<Void>> verifyEmail(@Body VerifyEmailRequest request);

    /** API Yêu cầu gửi lại mã OTP */
    @POST("api/v1/auth/resend-otp")
    Call<ApiResponse<Void>> resendOtp(@Body ResendOtpRequest request);

    /** API Yêu cầu quên mật khẩu (gửi OTP về email) */
    @POST("api/v1/auth/forgot-password")
    Call<ApiResponse<Void>> forgotPassword(@Body ForgotPasswordRequest request);

    /** API Đặt lại mật khẩu mới bằng mã OTP */
    @POST("api/v1/auth/reset-password")
    Call<ApiResponse<Void>> resetPassword(@Body ResetPasswordRequest request);
}