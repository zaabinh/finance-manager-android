package com.example.fintrack.app.core.network;

import android.content.Context;
import android.text.TextUtils;

import com.example.fintrack.R;
import com.google.gson.Gson;

import java.io.IOException;
import java.net.SocketTimeoutException;

import retrofit2.Response;

/**
 * Lớp tiện ích (Utility Class) dùng để chuyển đổi các lỗi kỹ thuật từ mạng (Exception, HTTP Status Code, Backend ErrorCode)
 * thành các thông báo dạng chữ thân thiện, dễ đọc bằng tiếng Việt cho người dùng.
 */
public class ErrorMapper {

    /**
     * Xử lý các lỗi ngoại lệ (Throwable) xảy ra khi gọi mạng (ví dụ: mất mạng, quá thời gian chờ).
     */
    public static String getErrorMessage(Context context, Throwable throwable) {
        if (context == null) {
            return "Không thể kết nối đến máy chủ";
        }
        if (throwable instanceof SocketTimeoutException) {
            return context.getString(R.string.error_timeout);
        } else if (throwable instanceof IOException) {
            return context.getString(R.string.error_connection);
        }
        return context.getString(R.string.error_unknown);
    }

    /**
     * Xử lý các mã lỗi HTTP và JSON ApiErrorResponse trả về từ Backend Spring Boot.
     */
    public static String getHttpErrorMessage(Context context, Response<?> response) {
        if (context == null) {
            return "Máy chủ gặp sự cố";
        }
        if (response == null) {
            return context.getString(R.string.error_server);
        }

        // 1. Thử parse errorBody từ JSON phản hồi của Backend
        if (response.errorBody() != null) {
            try {
                String errorJson = response.errorBody().string();
                ApiErrorResponse errorObj = new Gson().fromJson(errorJson, ApiErrorResponse.class);
                if (errorObj != null) {
                    String errorCode = errorObj.getErrorCode();
                    if (!TextUtils.isEmpty(errorCode)) {
                        switch (errorCode) {
                            case "AUTH_EMAIL_NOT_VERIFIED":
                                return "AUTH_EMAIL_NOT_VERIFIED"; // Dùng cờ đặc biệt để Activity nhận diện và mở Dialog xác thực
                            case "AUTH_USERNAME_ALREADY_EXISTS":
                                return "Tên người dùng đã được sử dụng.";
                            case "AUTH_EMAIL_ALREADY_EXISTS":
                                return "Email đã được đăng ký bởi tài khoản khác.";
                            case "AUTH_USER_LOCKED":
                                return "Tài khoản của bạn đã bị khóa.";
                            case "AUTH_USER_DISABLED":
                                return "Tài khoản của bạn đã bị vô hiệu hóa.";
                            case "AUTH_INVALID_CREDENTIALS":
                            case "AUTH_USERNAME_NOT_EXIST":
                                return "Tên đăng nhập hoặc mật khẩu không chính xác.";
                            case "AUTH_OTP_INVALID":
                                return "Mã OTP không chính xác. Vui lòng kiểm tra lại.";
                            case "AUTH_OTP_EXPIRED":
                                return "Mã OTP đã hết hạn. Vui lòng gửi lại mã mới.";
                            case "AUTH_RATE_LIMITED":
                            case "AUTH_OTP_ATTEMPTS_EXCEEDED":
                                return "Thao tác quá nhanh hoặc vượt quá số lần thử. Vui lòng thử lại sau.";
                            case "AUTH_ACCESS_TOKEN_INVALID":
                            case "AUTH_REFRESH_TOKEN_INVALID":
                                return context.getString(R.string.error_unauthorized);
                        }
                    }

                    // Nếu Backend có trả về message trực tiếp
                    if (!TextUtils.isEmpty(errorObj.getMessage()) && !"An unexpected error occurred".equals(errorObj.getMessage())) {
                        return errorObj.getMessage();
                    }

                    // Nếu có lỗi fieldErrors chi tiết
                    if (errorObj.getFieldErrors() != null && !errorObj.getFieldErrors().isEmpty()) {
                        ApiErrorResponse.FieldError firstError = errorObj.getFieldErrors().get(0);
                        if (!TextUtils.isEmpty(firstError.getMessage())) {
                            return firstError.getMessage();
                        }
                    }
                }
            } catch (Exception ignored) { }
        }

        // 2. Mặc định theo mã lỗi HTTP status code
        int code = response.code();
        if (code == 400) {
            return context.getString(R.string.error_bad_request);
        } else if (code == 401) {
            return context.getString(R.string.error_unauthorized);
        } else if (code == 403) {
            return context.getString(R.string.error_forbidden);
        } else if (code == 404) {
            return context.getString(R.string.error_not_found);
        } else if (code >= 500) {
            return context.getString(R.string.error_server);
        }

        return context.getString(R.string.error_unknown);
    }
}
