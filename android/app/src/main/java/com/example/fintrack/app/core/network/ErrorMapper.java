package com.example.fintrack.app.core.network;

import android.content.Context;

import com.example.fintrack.R;

import java.io.IOException;
import java.net.SocketTimeoutException;

import retrofit2.Response;

/**
 * Lớp tiện ích (Utility Class) dùng để chuyển đổi các lỗi kỹ thuật từ mạng (Exception, HTTP Status Code)
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
        // Lỗi quá thời gian chờ (Timeout) do mạng chậm hoặc server phản hồi quá lâu
        if (throwable instanceof SocketTimeoutException) {
            return context.getString(R.string.error_timeout);
        }
        // Lỗi kết nối mạng chung (ví dụ: không có internet, mất kết nối)
        else if (throwable instanceof IOException) {
            return context.getString(R.string.error_connection);
        }
        // Các lỗi không xác định khác
        return context.getString(R.string.error_unknown);
    }

    /**
     * Xử lý các mã lỗi HTTP trả về từ server (ví dụ: 400, 401, 404, 500...).
     */
    public static String getHttpErrorMessage(Context context, Response<?> response) {
        if (context == null) {
            return "Máy chủ gặp sự cố";
        }
        if (response == null) {
            return context.getString(R.string.error_server);
        }

        int code = response.code(); // Lấy mã lỗi HTTP

        if (code == 400) {
            return context.getString(R.string.error_bad_request); // Lỗi yêu cầu không hợp lệ
        } else if (code == 401) {
            return context.getString(R.string.error_unauthorized); // Lỗi chưa đăng nhập hoặc hết hạn token
        } else if (code == 403) {
            return context.getString(R.string.error_forbidden); // Lỗi không có quyền truy cập
        } else if (code == 404) {
            return context.getString(R.string.error_not_found); // Không tìm thấy tài nguyên/đường dẫn trên server
        } else if (code >= 500) {
            return context.getString(R.string.error_server); // Lỗi hệ thống phía Server (5xx)
        }

        return context.getString(R.string.error_unknown); // Các mã lỗi HTTP khác
    }
}