package com.example.fintrack.app.core.util;

/**
 * Lớp bọc sự kiện (Event Wrapper) dùng chung trong kiến trúc MVVM,
 * giúp ngăn chặn việc sự kiện (như hiện Toast, hiển thị lỗi, chuyển màn hình) bị kích hoạt lại
 * ngoài ý muốn mỗi khi Activity hoặc Fragment bị vẽ lại (ví dụ: khi xoay ngang/dọc màn hình).
 */
public class Event<T> {

    private final T content;                  // Nội dung chính của sự kiện
    private boolean hasBeenHandled = false;   // Cờ đánh dấu: true = đã xử lý, false = chưa xử lý

    // Khởi tạo sự kiện với nội dung truyền vào
    public Event(T content) {
        this.content = content;
    }

    /**
     * Lấy nội dung sự kiện.
     * - Nếu đã xử lý rồi -> trả về null (bỏ qua).
     * - Nếu chưa xử lý -> đánh dấu là đã xử lý (true) và trả về nội dung bên trong.
     */
    public T getContentIfNotHandled() {
        if (hasBeenHandled) {
            return null;
        } else {
            hasBeenHandled = true;
            return content;
        }
    }

    /** Xem trước nội dung sự kiện mà KHÔNG làm thay đổi trạng thái đã xử lý */
    public T peekContent() {
        return content;
    }

    /** Kiểm tra xem sự kiện này đã được xử lý hay chưa */
    public boolean hasBeenHandled() {
        return hasBeenHandled;
    }
}