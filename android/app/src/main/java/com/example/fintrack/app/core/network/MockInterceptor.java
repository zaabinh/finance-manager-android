package com.example.fintrack.app.core.network;

import androidx.annotation.NonNull;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Lớp giả lập dữ liệu mạng (Mock Interceptor) dùng trong quá trình kiểm thử (Testing) hoặc khi Backend chưa hoàn thiện.
 * Nó sẽ "chặn" các request gửi đi và tự động trả về dữ liệu JSON mẫu lập tức mà không cần gọi đến Server thật.
 */
public class MockInterceptor implements Interceptor {

    // Định nghĩa kiểu dữ liệu trả về là JSON
    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json");

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) {
        // Lấy đường dẫn (path) của API đang được gọi (ví dụ: "/login", "/register"...)
        String path = chain.request().url().encodedPath();

        // Giả lập độ trễ mạng (chờ 400 mili-giây) để giống như đang gọi mạng thật
        try {
            Thread.sleep(400);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        // Lấy đoạn JSON mẫu tương ứng với đường dẫn API
        String jsonResponse = getMockJsonResponse(path);

        // Tạo và trả về một Response giả lập chứa dữ liệu JSON mẫu với mã thành công 200 (OK)
        return new Response.Builder()
                .code(200)
                .message("OK")
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .body(ResponseBody.create(jsonResponse, MEDIA_TYPE_JSON))
                .addHeader("content-type", "application/json")
                .build();
    }

    /**
     * Hàm phụ trợ quyết định sẽ trả về chuỗi JSON mẫu nào dựa vào đường dẫn API gọi đến
     */
    private String getMockJsonResponse(String path) {
        if (path.endsWith("/login")) {
            return """
                    {
                      "success": true,
                      "message": "Đăng nhập thành công",
                      "data": {
                        "accessToken": "mock_access_token_123456789",
                        "refreshToken": "mock_refresh_token_987654321",
                        "userId": "user_demo_001",
                        "email": "demo@fintrack.com",
                        "name": "Nguyễn Văn Demo"
                      },
                      "code": 200
                    }""";
        } else if (path.endsWith("/register")) {
            return """
                    {
                      "success": true,
                      "message": "Đăng ký thành công. Vui lòng kiểm tra email để nhận mã OTP.",
                      "data": null,
                      "code": 200
                    }""";
        } else if (path.endsWith("/verify-email")) {
            return """
                    {
                      "success": true,
                      "message": "Xác thực email thành công!",
                      "data": null,
                      "code": 200
                    }""";
        } else if (path.endsWith("/resend-otp")) {
            return """
                    {
                      "success": true,
                      "message": "Mã OTP mới đã được gửi đến email của bạn.",
                      "data": null,
                      "code": 200
                    }""";
        } else if (path.endsWith("/forgot-password")) {
            return """
                    {
                      "success": true,
                      "message": "Yêu cầu đặt lại mật khẩu đã được gửi. Vui lòng kiểm tra OTP.",
                      "data": null,
                      "code": 200
                    }""";
        } else if (path.endsWith("/reset-password")) {
            return """
                    {
                      "success": true,
                      "message": "Đặt lại mật khẩu thành công!",
                      "data": null,
                      "code": 200
                    }""";
        } else if (path.endsWith("/refresh-token")) {
            return """
                    {
                      "success": true,
                      "message": "Làm mới token thành công",
                      "data": {
                        "accessToken": "mock_refreshed_access_token_12345",
                        "refreshToken": "mock_refreshed_refresh_token_67890",
                        "userId": "user_demo_001",
                        "email": "demo@fintrack.com",
                        "name": "Nguyễn Văn Demo"
                      },
                      "code": 200
                    }""";
        } else if (path.endsWith("/logout") || path.endsWith("/logout-all")) {
            return """
                    {
                      "success": true,
                      "message": "Đăng xuất thành công",
                      "data": null,
                      "code": 200
                    }""";
        } else if (path.contains("/profile") || path.contains("/user")) {
            return """
                    {
                      "success": true,
                      "message": "Lấy thông tin thành công",
                      "data": {
                        "userId": "user_demo_001",
                        "email": "demo@fintrack.com",
                        "name": "Nguyễn Văn Demo",
                        "phone": "0987654321"
                      },
                      "code": 200
                    }""";
        } else if (path.contains("/wallets")) {
            return """
                    {
                      "success": true,
                      "message": "Lấy danh sách ví thành công",
                      "data": [
                        {
                          "id": "wallet_01",
                          "name": "Ví chính",
                          "balance": 15000000.0,
                          "currency": "VND"
                        },
                        {
                          "id": "wallet_02",
                          "name": "Tiết kiệm",
                          "balance": 50000000.0,
                          "currency": "VND"
                        }
                      ],
                      "code": 200
                    }""";
        } else if (path.contains("/categories")) {
            return """
                    {
                      "success": true,
                      "message": "Lấy danh mục thành công",
                      "data": [
                        { "id": "cat_01", "name": "Ăn uống", "type": "EXPENSE", "icon": "ic_food" },
                        { "id": "cat_02", "name": "Lương", "type": "INCOME", "icon": "ic_salary" },
                        { "id": "cat_03", "name": "Di chuyển", "type": "EXPENSE", "icon": "ic_transport" }
                      ],
                      "code": 200
                    }""";
        } else if (path.contains("/transactions")) {
            return """
                    {
                      "success": true,
                      "message": "Lấy danh sách giao dịch thành công",
                      "data": [
                        {
                          "id": "txn_01",
                          "title": "Ăn trưa",
                          "amount": 50000.0,
                          "type": "EXPENSE",
                          "date": "2026-10-28",
                          "category": "Ăn uống",
                          "wallet": "Ví chính"
                        },
                        {
                          "id": "txn_02",
                          "title": "Nhận lương tháng 10",
                          "amount": 15000000.0,
                          "type": "INCOME",
                          "date": "2026-10-27",
                          "category": "Lương",
                          "wallet": "Ví chính"
                        }
                      ],
                      "code": 200
                    }""";
        }

        // Phản hồi mẫu mặc định cho các API khác chưa được định nghĩa cụ thể
        return """
                {
                  "success": true,
                  "message": "Thành công (Mock Mode)",
                  "data": null,
                  "code": 200
                }""";
    }
}
