package com.example.fintrack.app.core.network;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.fintrack.app.auth.remote.AuthApiService;
import com.example.fintrack.app.auth.remote.LoginResponse;
import com.example.fintrack.app.auth.remote.RefreshTokenRequest;
import com.example.fintrack.app.core.session.SessionManager;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

/**
 * Tự động gọi API refresh-token khi OkHttp nhận phản hồi 401 Unauthorized từ Server.
 */
public class TokenAuthenticator implements Authenticator {

    private final Context context;

    public TokenAuthenticator(Context context) {
        // Dùng applicationContext để tránh rò rỉ bộ nhớ (Memory Leak) liên quan đến Activity
        this.context = context.getApplicationContext();
    }

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, @NonNull Response response) throws IOException {

        // BƯỚC 1: Chống lặp vô tận (Infinite Loop Protection)
        // Nếu request này đã thử gửi lại quá 2 lần mà vẫn lỗi 401, nghĩa là Refresh Token cũng chết rồi -> Xóa phiên đăng nhập và dừng lại.
        if (responseCount(response) >= 2) {
            SessionManager.getInstance(context).clearSession();
            return null; // Trả về null để báo OkHttp dừng việc thử lại
        }

        SessionManager sessionManager = SessionManager.getInstance(context);
        String refreshToken = sessionManager.getRefreshToken();

        // BƯỚC 2: Kiểm tra xem có Refresh Token trong máy không
        // Nếu không có Refresh Token -> Không thể làm mới, bắt buộc phải xóa phiên đăng nhập để bắt đăng nhập lại từ đầu.
        if (TextUtils.isEmpty(refreshToken)) {
            sessionManager.clearSession();
            return null;
        }

        // BƯỚC 3: Gọi API làm mới token (Yêu cầu phải gọi đồng bộ - .execute() thay vì .enqueue())
        AuthApiService authApiService = ApiClient.createService(context, AuthApiService.class);
        retrofit2.Response<ApiResponse<LoginResponse>> refreshResponse =
                authApiService.refreshToken(new RefreshTokenRequest(refreshToken)).execute();

        // BƯỚC 4: Kiểm tra kết quả gọi API làm mới token
        if (refreshResponse.isSuccessful() && refreshResponse.body() != null && refreshResponse.body().isSuccess()) {
            LoginResponse newTokens = refreshResponse.body().getData();

            // Nếu server trả về Token mới hợp lệ
            if (newTokens != null && !TextUtils.isEmpty(newTokens.getAccessToken())) {
                // Lưu lại Access Token và Refresh Token mới vào bộ nhớ máy
                sessionManager.saveAuthToken(newTokens.getAccessToken(), newTokens.getRefreshToken());

                // BƯỚC 5: Tự động lặp lại (Retry) Request ban đầu nhưng thay bằng Access Token MỚI
                return response.request().newBuilder()
                        .header("Authorization", "Bearer " + newTokens.getAccessToken())
                        .build();
            }
        }

        // BƯỚC 6: Nếu quá trình gọi API refresh token bị lỗi (ví dụ token hết hạn thật, server từ chối)
        // -> Xóa sạch phiên đăng nhập, đá văng user về màn hình đăng nhập.
        sessionManager.clearSession();
        return null;
    }

    /**
     * Hàm phụ trợ đếm xem một Request đã bị lỗi 401 và được thử lại (retry) bao nhiêu lần rồi.
     * Giúp tránh tình trạng lặp vô tận (gọi refresh liên tục không bao giờ dừng nếu server cứ trả về lỗi).
     */
    private int responseCount(Response response) {
        int result = 1;
        while ((response = response.priorResponse()) != null) {
            result++;
        }
        return result;
    }
}