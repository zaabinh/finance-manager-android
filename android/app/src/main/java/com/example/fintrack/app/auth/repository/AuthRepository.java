package com.example.fintrack.app.auth.repository;

import com.example.fintrack.app.auth.remote.AuthApiService;
import com.example.fintrack.app.auth.remote.ChangePasswordRequest;
import com.example.fintrack.app.auth.remote.LoginRequest;
import com.example.fintrack.app.auth.remote.LoginResponse;
import com.example.fintrack.app.auth.remote.RefreshTokenRequest;
import com.example.fintrack.app.auth.remote.RegisterRequest;
import com.example.fintrack.app.auth.remote.ResendOtpRequest;
import com.example.fintrack.app.auth.remote.ResetPasswordRequest;
import com.example.fintrack.app.auth.remote.TokenResponse;
import com.example.fintrack.app.auth.remote.VerifyEmailRequest;
import com.example.fintrack.app.core.network.ApiCalls;
import com.example.fintrack.app.core.network.RequestHandle;
import com.example.fintrack.app.core.network.Resource;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Transport only. The ViewModel owns state and session side effects. */
public class AuthRepository {
    private final AuthApiService apiService;
    private final Executor delivery;
    public AuthRepository(AuthApiService apiService, Executor delivery) {
        this.apiService = apiService; this.delivery = delivery;
    }
    public RequestHandle login(String usernameOrEmail, String password, Consumer<Resource<LoginResponse>> result) {
        return ApiCalls.enqueue(apiService.login(new LoginRequest(usernameOrEmail, password)), true, delivery, result);
    }
    public RequestHandle register(String username, String email, String password, Consumer<Resource<Void>> result) {
        return ApiCalls.enqueue(apiService.register(new RegisterRequest(username, email, password)), false, delivery, result);
    }
    public RequestHandle verifyEmail(String email, String otp, Consumer<Resource<Void>> result) {
        return ApiCalls.enqueue(apiService.verifyEmail(new VerifyEmailRequest(email, otp)), false, delivery, result);
    }
    public RequestHandle resendVerification(String email, Consumer<Resource<Void>> result) {
        return ApiCalls.enqueue(apiService.resendVerification(new ResendOtpRequest(email)), false, delivery, result);
    }
    public RequestHandle resendOtp(String email, Consumer<Resource<Void>> result) {
        return ApiCalls.enqueue(apiService.resendVerification(new ResendOtpRequest(email)), false, delivery, result);
    }
    public RequestHandle forgotPassword(String email, Consumer<Resource<Void>> result) {
        return ApiCalls.enqueue(apiService.forgotPassword(new ResendOtpRequest(email)), false, delivery, result);
    }
    public RequestHandle resetPassword(String email, String otp, String newPassword, Consumer<Resource<Void>> result) {
        return ApiCalls.enqueue(apiService.resetPassword(new ResetPasswordRequest(email, otp, newPassword)), false, delivery, result);
    }
    public RequestHandle changePassword(String currentPassword, String newPassword, Consumer<Resource<TokenResponse>> result) {
        return ApiCalls.enqueue(apiService.changePassword(new ChangePasswordRequest(currentPassword, newPassword)), true, delivery, result);
    }
    public RequestHandle logout(String refreshToken, Consumer<Resource<Void>> result) {
        return ApiCalls.enqueue(apiService.logout(new RefreshTokenRequest(refreshToken)), false, delivery, result);
    }
}
