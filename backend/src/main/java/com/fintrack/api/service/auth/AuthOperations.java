package com.fintrack.api.service.auth;

import com.fintrack.api.dto.request.auth.*;
import com.fintrack.api.dto.response.auth.LoginResponse;
import com.fintrack.api.dto.response.auth.TokenResponse;

public interface AuthOperations {
    void register(RegisterRequest request);
    LoginResponse login(LoginRequest request, ClientMetadata client);
    TokenResponse refresh(String rawRefreshToken, ClientMetadata client);
    void logout(String rawRefreshToken);
    void resetPassword(ResetPasswordRequest request);
    TokenResponse changePassword(java.util.UUID userId, ChangePasswordRequest request, ClientMetadata client);
    void verifyEmail(VerifyEmailRequest request);
    void resendVerification(ResendOtpRequest request);
    void forgotPassword(ResendOtpRequest request);
}
