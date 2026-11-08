package com.example.fintrack.app.auth.remote;

import com.example.fintrack.app.core.network.ApiResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * Interface định nghĩa các API endpoint xác thực chính xác theo Backend Spring Boot:
 * register, login, refresh, logout, logout-all, change-password, verify-email, resend-verification, forgot-password, reset-password.
 */
public interface AuthApiService {

    /** Đăng ký tài khoản mới */
    @POST("api/v1/auth/register")
    Call<ApiResponse<Void>> register(@Body RegisterRequest request);

    /** Đăng nhập tài khoản */
    @POST("api/v1/auth/login")
    Call<ApiResponse<LoginResponse>> login(@Body LoginRequest request);

    /** Làm mới Access Token bằng Refresh Token */
    @POST("api/v1/auth/refresh")
    Call<ApiResponse<TokenResponse>> refreshToken(@Body RefreshTokenRequest request);

    /** Đăng xuất thiết bị hiện tại */
    @POST("api/v1/auth/logout")
    Call<ApiResponse<Void>> logout(@Body RefreshTokenRequest request);

    /** Đổi mật khẩu tài khoản (khi đã đăng nhập) */
    @POST("api/v1/auth/change-password")
    Call<ApiResponse<TokenResponse>> changePassword(@Body ChangePasswordRequest request);

    /** Xác thực email bằng mã OTP */
    @POST("api/v1/auth/verify-email")
    Call<ApiResponse<Void>> verifyEmail(@Body VerifyEmailRequest request);

    /** Gửi lại email xác nhận tài khoản */
    @POST("api/v1/auth/resend-verification")
    Call<ApiResponse<Void>> resendVerification(@Body ResendOtpRequest request);

    /** Yêu cầu quên mật khẩu (gửi OTP về email) */
    @POST("api/v1/auth/forgot-password")
    Call<ApiResponse<Void>> forgotPassword(@Body ResendOtpRequest request);

    /** Đặt lại mật khẩu mới bằng mã OTP */
    @POST("api/v1/auth/reset-password")
    Call<ApiResponse<Void>> resetPassword(@Body ResetPasswordRequest request);
}
