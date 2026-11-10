package com.example.fintrack.app.core.network;

/**
 * Lớp bọc dữ liệu (Wrapper Class) phổ biến trong kiến trúc MVVM,
 * dùng để quản lý và truyền tải 3 trạng thái của một tác vụ (như gọi API):
 * Đang tải (Loading), Thành công (Success), và Thất bại (Error).
 * @param <T> Kiểu dữ liệu của kết quả trả về (ví dụ: LoginResponse, Void, List...)
 */
public class Resource<T> {

    // Định nghĩa 3 trạng thái chính của quá trình xử lý
    public enum Status {
        SUCCESS, // Thành công (có dữ liệu)
        ERROR,   // Thất bại (có thông báo lỗi)
        LOADING  // Đang tải / đang xử lý (ví dụ: đang hiện vòng xoay progress bar)
    }

    private final Status status;  // Trạng thái hiện tại
    private final T data;         // Dữ liệu trả về (nếu có)
    private final String message; // Thông báo lỗi (nếu có)

    // Hàm khởi tạo riêng tư (chỉ gọi thông qua các hàm static bên dưới)
    private Resource(Status status, T data, String message) {
        this.status = status;
        this.data = data;
        this.message = message;
    }

    /** Tạo trạng thái THÀNH CÔNG, kèm theo dữ liệu nhận được */
    public static <T> Resource<T> success(T data) {
        return new Resource<>(Status.SUCCESS, data, null);
    }

    /** Tạo trạng thái LỖI, kèm theo câu thông báo lỗi và dữ liệu tạm (nếu có) */
    public static <T> Resource<T> error(String msg, T data) {
        return new Resource<>(Status.ERROR, data, msg);
    }

    /** Tạo trạng thái ĐANG TẢI */
    public static <T> Resource<T> loading(T data) {
        return new Resource<>(Status.LOADING, data, null);
    }

    // --- Các hàm Getter để lấy thông tin trạng thái, dữ liệu và thông báo ---

    public Status getStatus() {
        return status;
    }

    public T getData() {
        return data;
    }

    public String getMessage() {
        return message;
    }
}