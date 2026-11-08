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
 * Tự động gọi API refresh-token khi OkHttp nhận phản hồi 401 Unauthorized từ Server.
 * Áp dụng cơ chế đồng bộ hóa (synchronized) và kiểm tra race condition khi nhiều request nhận 401 đồng thời.
 */
public class TokenAuthenticator implements Authenticator {

    private final Context context;

    public TokenAuthenticator(Context context) {
        this.context = context.getApplicationContext();
    }

    @Nullable
    @Override
    public synchronized Request authenticate(@Nullable Route route, @NonNull Response response) throws IOException {

        // 1. Chống lặp vô hạn (Infinite Loop Protection)
        if (responseCount(response) >= 2) {
            SessionManager.getInstance(context).clearSession();
            return null;
        }

        SessionManager sessionManager = SessionManager.getInstance(context);
        String currentAccessToken = sessionManager.getAccessToken();

        // 2. Kiểm tra Race Condition khi nhiều request nhận 401 đồng thời
        String requestAuthHeader = response.request().header("Authorization");
        if (!TextUtils.isEmpty(requestAuthHeader) && requestAuthHeader.startsWith("Bearer ")) {
            String sentToken = requestAuthHeader.substring(7);
            if (!TextUtils.isEmpty(currentAccessToken) && !sentToken.equals(currentAccessToken)) {
                return response.request().newBuilder()
                        .header("Authorization", "Bearer " + currentAccessToken)
                        .build();
            }
        }

        String refreshToken = sessionManager.getRefreshToken();
        if (TextUtils.isEmpty(refreshToken)) {
            sessionManager.clearSession();
            return null;
        }

        // 3. Gọi API làm mới token sử dụng RefreshClient riêng biệt
        AuthApiService authApiService = ApiClient.getRefreshClient(context).create(AuthApiService.class);
        retrofit2.Response<ApiResponse<TokenResponse>> refreshResponse;
        try {
            refreshResponse = authApiService.refreshToken(new RefreshTokenRequest(refreshToken)).execute();
        } catch (Exception e) {
            return null;
        }

        // 4. Kiểm tra kết quả gọi API làm mới token
        if (refreshResponse.isSuccessful() && refreshResponse.body() != null && refreshResponse.body().isSuccess()) {
            TokenResponse newTokens = refreshResponse.body().getData();
            if (newTokens != null && !TextUtils.isEmpty(newTokens.getAccessToken())) {
                sessionManager.saveAuthToken(newTokens.getAccessToken(), newTokens.getRefreshToken());

                return response.request().newBuilder()
                        .header("Authorization", "Bearer " + newTokens.getAccessToken())
                        .build();
            }
        }

        // 5. Nếu Refresh Token thực sự hết hạn hoặc không hợp lệ -> Kết thúc phiên
        sessionManager.clearSession();
        return null;
    }

    private int responseCount(Response response) {
        int result = 1;
        while ((response = response.priorResponse()) != null) {
            result++;
        }
        return result;
    }
}
