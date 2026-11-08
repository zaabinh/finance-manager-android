package com.example.fintrack.app.core.network;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.fintrack.app.auth.remote.AuthApiService;
import com.example.fintrack.app.auth.remote.RefreshTokenRequest;
import com.example.fintrack.app.auth.remote.TokenResponse;
import com.example.fintrack.app.core.session.SessionManager;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

/**
 * Tự động gọi API refresh-token khi OkHttp nhận phản hồi 401 Unauthorized từ Server đối với các Protected API.
 * Phân biệt 401 của Access Token hết hạn với lỗi 401 của Login/Register/Refresh public endpoints.
 * Bảo vệ session không bị xóa nhầm khi gặp lỗi mạng tạm thời (transient network failure).
 */
public class TokenAuthenticator implements Authenticator {

    private final Context context;

    public TokenAuthenticator(Context context) {
        this.context = context.getApplicationContext();
    }

    @Nullable
    @Override
    public synchronized Request authenticate(@Nullable Route route, @NonNull Response response) throws IOException {

        // 1. Kiểm tra URL request: Nếu 401 đến từ các API Authentication công khai (login, register, refresh, OTP, reset-password)
        // hoặc request KHÔNG gửi kèm Bearer Access Token -> Bỏ qua không refresh và KHÔNG xóa session.
        String path = response.request().url().encodedPath();
        String requestAuthHeader = response.request().header("Authorization");

        if (isAuthEndpoint(path) || TextUtils.isEmpty(requestAuthHeader) || !requestAuthHeader.startsWith("Bearer ")) {
            return null;
        }

        // 2. Chống lặp vô hạn (Infinite Loop Protection)
        if (responseCount(response) >= 2) {
            SessionManager.getInstance(context).clearSession();
            return null;
        }

        SessionManager sessionManager = SessionManager.getInstance(context);
        String currentAccessToken = sessionManager.getAccessToken();

        // 3. Race Condition: Nếu token đã được một request đồng thời khác làm mới thành công trước đó
        // -> Dùng ngay access token mới trong session để retry request.
        String sentToken = requestAuthHeader.substring(7);
        if (!TextUtils.isEmpty(currentAccessToken) && !sentToken.equals(currentAccessToken)) {
            return response.request().newBuilder()
                    .header("Authorization", "Bearer " + currentAccessToken)
                    .build();
        }

        String refreshToken = sessionManager.getRefreshToken();
        if (TextUtils.isEmpty(refreshToken)) {
            sessionManager.clearSession();
            return null;
        }

        // 4. Gọi API refresh token bằng RefreshClient riêng biệt
        AuthApiService authApiService = ApiClient.getRefreshClient(context).create(AuthApiService.class);
        retrofit2.Response<ApiResponse<TokenResponse>> refreshResponse;
        try {
            refreshResponse = authApiService.refreshToken(new RefreshTokenRequest(refreshToken)).execute();
        } catch (IOException e) {
            // Lỗi mạng tạm thời khi gọi refresh -> Trả null để OkHttp báo lỗi mạng cho caller, KHÔNG xóa session!
            return null;
        }

        // 5. Kiểm tra kết quả làm mới token
        if (refreshResponse.isSuccessful() && refreshResponse.body() != null && refreshResponse.body().isSuccess()) {
            TokenResponse newTokens = refreshResponse.body().getData();
            if (newTokens != null && !TextUtils.isEmpty(newTokens.getAccessToken())) {
                sessionManager.saveAuthToken(newTokens.getAccessToken(), newTokens.getRefreshToken());

                return response.request().newBuilder()
                        .header("Authorization", "Bearer " + newTokens.getAccessToken())
                        .build();
            }
        }

        // 6. Nếu Server xác nhận Refresh Token thực sự hết hạn hoặc không hợp lệ -> Xóa phiên đăng nhập
        sessionManager.clearSession();
        return null;
    }

    private boolean isAuthEndpoint(String path) {
        if (TextUtils.isEmpty(path)) return false;
        return path.contains("/api/v1/auth/login") ||
               path.contains("/api/v1/auth/register") ||
               path.contains("/api/v1/auth/refresh") ||
               path.contains("/api/v1/auth/verify-email") ||
               path.contains("/api/v1/auth/resend-verification") ||
               path.contains("/api/v1/auth/forgot-password") ||
               path.contains("/api/v1/auth/reset-password");
    }

    private int responseCount(Response response) {
        int result = 1;
        while ((response = response.priorResponse()) != null) {
            result++;
        }
        return result;
    }
}
