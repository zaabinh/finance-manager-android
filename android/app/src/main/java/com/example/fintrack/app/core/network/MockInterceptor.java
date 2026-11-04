package com.example.fintrack.app.core.network;

import androidx.annotation.NonNull;

import java.io.IOException;

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
    public Response intercept(@NonNull Chain chain) throws IOException {
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
            return "{\n" +
                    "  \"success\": true,\n" +
                    "  \"message\": \"Đăng nhập thành công\",\n" +
                    "  \"data\": {\n" +
                    "    \"accessToken\": \"mock_access_token_123456789\",\n" +
                    "    \"refreshToken\": \"mock_refresh_token_987654321\",\n" +
                    "    \"userId\": \"user_demo_001\",\n" +
                    "    \"email\": \"demo@fintrack.com\",\n" +
                    "    \"name\": \"Nguyễn Văn Demo\"\n" +
                    "  },\n" +
                    "  \"code\": 200\n" +
                    "}";
        } else if (path.endsWith("/register")) {
            return "{\n" +
                    "  \"success\": true,\n" +
                    "  \"message\": \"Đăng ký thành công. Vui lòng kiểm tra email để nhận mã OTP.\",\n" +
                    "  \"data\": null,\n" +
                    "  \"code\": 200\n" +
                    "}";
        } else if (path.endsWith("/verify-email")) {
            return "{\n" +
                    "  \"success\": true,\n" +
                    "  \"message\": \"Xác thực email thành công!\",\n" +
                    "  \"data\": null,\n" +
                    "  \"code\": 200\n" +
                    "}";
        } else if (path.endsWith("/resend-otp")) {
            return "{\n" +
                    "  \"success\": true,\n" +
                    "  \"message\": \"Mã OTP mới đã được gửi đến email của bạn.\",\n" +
                    "  \"data\": null,\n" +
                    "  \"code\": 200\n" +
                    "}";
        } else if (path.endsWith("/forgot-password")) {
            return "{\n" +
                    "  \"success\": true,\n" +
                    "  \"message\": \"Yêu cầu đặt lại mật khẩu đã được gửi. Vui lòng kiểm tra OTP.\",\n" +
                    "  \"data\": null,\n" +
                    "  \"code\": 200\n" +
                    "}";
        } else if (path.endsWith("/reset-password")) {
            return "{\n" +
                    "  \"success\": true,\n" +
                    "  \"message\": \"Đặt lại mật khẩu thành công!\",\n" +
                    "  \"data\": null,\n" +
                    "  \"code\": 200\n" +
                    "}";
        } else if (path.contains("/profile") || path.contains("/user")) {
            return "{\n" +
                    "  \"success\": true,\n" +
                    "  \"message\": \"Lấy thông tin thành công\",\n" +
                    "  \"data\": {\n" +
                    "    \"userId\": \"user_demo_001\",\n" +
                    "    \"email\": \"demo@fintrack.com\",\n" +
                    "    \"name\": \"Nguyễn Văn Demo\",\n" +
                    "    \"phone\": \"0987654321\"\n" +
                    "  },\n" +
                    "  \"code\": 200\n" +
                    "}";
        } else if (path.contains("/wallets")) {
            return "{\n" +
                    "  \"success\": true,\n" +
                    "  \"message\": \"Lấy danh sách ví thành công\",\n" +
                    "  \"data\": [\n" +
                    "    {\n" +
                    "      \"id\": \"wallet_01\",\n" +
                    "      \"name\": \"Ví chính\",\n" +
                    "      \"balance\": 15000000.0,\n" +
                    "      \"currency\": \"VND\"\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"id\": \"wallet_02\",\n" +
                    "      \"name\": \"Tiết kiệm\",\n" +
                    "      \"balance\": 50000000.0,\n" +
                    "      \"currency\": \"VND\"\n" +
                    "    }\n" +
                    "  ],\n" +
                    "  \"code\": 200\n" +
                    "}";
        } else if (path.contains("/categories")) {
            return "{\n" +
                    "  \"success\": true,\n" +
                    "  \"message\": \"Lấy danh mục thành công\",\n" +
                    "  \"data\": [\n" +
                    "    { \"id\": \"cat_01\", \"name\": \"Ăn uống\", \"type\": \"EXPENSE\", \"icon\": \"ic_food\" },\n" +
                    "    { \"id\": \"cat_02\", \"name\": \"Lương\", \"type\": \"INCOME\", \"icon\": \"ic_salary\" },\n" +
                    "    { \"id\": \"cat_03\", \"name\": \"Di chuyển\", \"type\": \"EXPENSE\", \"icon\": \"ic_transport\" }\n" +
                    "  ],\n" +
                    "  \"code\": 200\n" +
                    "}";
        } else if (path.contains("/transactions")) {
            return "{\n" +
                    "  \"success\": true,\n" +
                    "  \"message\": \"Lấy danh sách giao dịch thành công\",\n" +
                    "  \"data\": [\n" +
                    "    {\n" +
                    "      \"id\": \"txn_01\",\n" +
                    "      \"title\": \"Ăn trưa\",\n" +
                    "      \"amount\": 50000.0,\n" +
                    "      \"type\": \"EXPENSE\",\n" +
                    "      \"date\": \"2026-10-28\",\n" +
                    "      \"category\": \"Ăn uống\",\n" +
                    "      \"wallet\": \"Ví chính\"\n" +
                    "    },\n" +
                    "    {\n" +
                    "      \"id\": \"txn_02\",\n" +
                    "      \"title\": \"Nhận lương tháng 10\",\n" +
                    "      \"amount\": 15000000.0,\n" +
                    "      \"type\": \"INCOME\",\n" +
                    "      \"date\": \"2026-10-27\",\n" +
                    "      \"category\": \"Lương\",\n" +
                    "      \"wallet\": \"Ví chính\"\n" +
                    "    }\n" +
                    "  ],\n" +
                    "  \"code\": 200\n" +
                    "}";
        }

        // Phản hồi mẫu mặc định cho các API khác chưa được định nghĩa cụ thể
        return "{\n" +
                "  \"success\": true,\n" +
                "  \"message\": \"Thành công (Mock Mode)\",\n" +
                "  \"data\": null,\n" +
                "  \"code\": 200\n" +
                "}";
    }
}
