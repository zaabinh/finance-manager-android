package com.example.fintrack.app.core.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.fintrack.app.auth.remote.AuthApiService;
import com.example.fintrack.app.auth.remote.RefreshTokenRequest;
import com.example.fintrack.app.auth.remote.TokenResponse;
import com.example.fintrack.app.core.session.SessionSnapshot;
import com.example.fintrack.app.core.session.SessionStore;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

/**
 * Tự động gọi API refresh-token khi OkHttp nhận phản hồi 401 Unauthorized từ Server đối với các Protected API.
 * Phân biệt 401 của Access Token hết hạn với lỗi 401 của Login/Register/Refresh public endpoints.
 * Bảo vệ session không bị xóa nhầm khi gặp lỗi mạng tạm thời (transient network failure), 429 Rate Limit hoặc 5xx Server Error.
 */
public class TokenAuthenticator implements Authenticator {

    private final SessionStore sessionStore;
    private final AuthApiService authApiService;

    public TokenAuthenticator(SessionStore sessionStore, AuthApiService refreshApiService) {
        this.sessionStore = sessionStore;
        this.authApiService = refreshApiService;
    }

    @Nullable
    @Override
    public synchronized Request authenticate(@Nullable Route route, @NonNull Response response) throws IOException {

        // 1. Kiểm tra URL request: Nếu 401 đến từ các API Authentication công khai (login, register, refresh, OTP, reset-password)
        // hoặc request KHÔNG gửi kèm Bearer Access Token -> Bỏ qua không refresh và KHÔNG xóa session.
        String path = response.request().url().encodedPath();
        String requestAuthHeader = response.request().header("Authorization");

        if (isAuthEndpoint(path) || requestAuthHeader == null || !requestAuthHeader.startsWith("Bearer ")) {
            return null;
        }

        // 2. Request thuộc về một phiên đăng nhập cũ (đã đăng xuất, đổi tài khoản hoặc đăng nhập lại cùng tài khoản)
        // -> Không refresh, không retry bằng token của phiên mới và KHÔNG đụng tới phiên hiện tại.
        SessionSnapshot origin = response.request().tag(SessionSnapshot.class);
        SessionSnapshot session = sessionStore.current();
        if (origin == null || !session.loggedIn || session.generation != origin.generation) {
            return null;
        }

        // 3. Chống lặp vô hạn (Infinite Loop Protection)
        if (responseCount(response) >= 2) {
            sessionStore.clear(session);
            return null;
        }

        // 4. Race Condition: Nếu token đã được một request đồng thời khác (cùng phiên) làm mới thành công trước đó
        // -> Dùng ngay access token mới trong session để retry request.
        String sentToken = requestAuthHeader.substring(7);
        if (!isEmpty(session.accessToken) && !sentToken.equals(session.accessToken)) {
            return response.request().newBuilder()
                    .header("Authorization", "Bearer " + session.accessToken)
                    .build();
        }

        if (isEmpty(session.refreshToken)) {
            sessionStore.clear(session);
            return null;
        }

        // 5. Gọi API refresh token bằng RefreshClient riêng biệt
        retrofit2.Response<ApiResponse<TokenResponse>> refreshResponse;
        try {
            refreshResponse = authApiService.refreshToken(new RefreshTokenRequest(session.refreshToken)).execute();
        } catch (IOException e) {
            // Lỗi mạng tạm thời khi gọi refresh -> Trả null để OkHttp báo lỗi mạng cho caller, KHÔNG xóa session!
            return null;
        }

        // 6. Kiểm tra kết quả làm mới token
        if (refreshResponse.isSuccessful() && refreshResponse.body() != null && refreshResponse.body().isSuccess()) {
            TokenResponse newTokens = refreshResponse.body().getData();
            if (newTokens != null && !isEmpty(newTokens.getAccessToken())) {
                // Phiên đã thay đổi trong lúc refresh -> bỏ token mới, không retry.
                if (!sessionStore.updateTokens(session, newTokens.getAccessToken(), newTokens.getRefreshToken())) {
                    return null;
                }

                return response.request().newBuilder()
                        .header("Authorization", "Bearer " + newTokens.getAccessToken())
                        .build();
            }
        }

        // 7. Xử lý khi API refresh token không thành công:
        // CHỈ XÓA SESSION (của đúng phiên này) khi Server xác nhận Refresh Token thực sự hết hạn hoặc không hợp lệ (HTTP 401 hoặc 403).
        // Đối với các lỗi tạm thời như Rate Limit (429) hoặc Lỗi Máy chủ (500, 502, 503), BẢO TỒN SESSION để người dùng có thể thử lại sau.
        int refreshStatusCode = refreshResponse.code();
        if (refreshStatusCode == 401 || refreshStatusCode == 403) {
            sessionStore.clear(session);
        }

        return null;
    }

    // android.text.TextUtils is not available to JVM unit tests.
    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private boolean isAuthEndpoint(String path) {
        if (isEmpty(path)) return false;
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
