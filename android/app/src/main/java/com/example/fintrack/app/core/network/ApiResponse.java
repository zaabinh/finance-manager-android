package com.example.fintrack.app.core.network;

import com.google.gson.annotations.SerializedName;

/**
 * Lớp chuẩn hóa (Wrapper Class) dùng chung cho tất cả các dữ liệu phản hồi (Response) từ Server.
 * Giúp ứng dụng dễ dàng đọc trạng thái thành công/thất bại, thông báo, mã lỗi và dữ liệu trả về.
 * @param <T> Kiểu dữ liệu cụ thể của phần "data" (có thể là LoginResponse, List<Item>, hoặc Void...)
 */
public class ApiResponse<T> {

    // Trạng thái yêu cầu (true: thành công, false: thất bại)
    @SerializedName("success")
    private boolean success;

    // Tin nhắn thông báo từ server (ví dụ: "Đăng nhập thành công", "Mật khẩu không đúng")
    @SerializedName("message")
    private String message;

    // Dữ liệu chính trả về từ server (kiểu linh hoạt tùy thuộc vào từng API gọi)
    @SerializedName("data")
    private T data;

    // Mã trạng thái/mã lỗi riêng của server (ví dụ: 200, 400, 401...)
    @SerializedName("code")
    private int code;

    // Constructor mặc định (cần thiết cho Gson khi chuyển đổi JSON sang đối tượng Java)
    public ApiResponse() {}

    // Constructor đầy đủ tham số để khởi tạo đối tượng khi cần
    public ApiResponse(boolean success, String message, T data, int code) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.code = code;
    }

    // --- Các phương thức Getter và Setter để lấy và gán giá trị ---

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }
}