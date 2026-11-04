package com.example.fintrack.app.category.model;

import java.io.Serializable;

/**
 * CategoryItem: Lớp mô hình (Model / POJO) đại diện cho một danh mục thu hoặc chi
 * trong ứng dụng FinTrack. Triển khai Serializable để có thể truyền đối tượng này
 * qua lại giữa các Activity dễ dàng.
 */
public class CategoryItem implements Serializable {

    private String id;              // Mã định danh duy nhất của danh mục (Ví dụ: "c1", "c3"...)
    private String name;            // Tên hiển thị của danh mục (Ví dụ: "Ăn uống", "Lương tháng"...)
    private String type;            // Loại danh mục: "EXPENSE" (Chi tiêu) hoặc "INCOME" (Thu nhập)
    private int iconResId;          // ID tài nguyên hình ảnh (Icon) đại diện cho danh mục (Ví dụ: R.drawable.ic_coffee)
    private String description;     // Mô tả chi tiết về danh mục
    private boolean isActive;       // Trạng thái hoạt động: true = Đang hoạt động, false = Đã vô hiệu hóa

    /**
     * Constructor mặc định (không tham số)
     * Mặc định khi tạo mới, trạng thái (isActive) sẽ là true.
     */
    public CategoryItem() {
        this.isActive = true;
    }

    /**
     * Constructor đầy đủ tham số để khởi tạo nhanh một đối tượng danh mục hoàn chỉnh
     */
    public CategoryItem(String id, String name, String type, int iconResId, String description, boolean isActive) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.iconResId = iconResId;
        this.description = description;
        this.isActive = isActive;
    }

    // --- Các phương thức Getter và Setter để truy xuất và cập nhật giá trị các thuộc tính ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getIconResId() {
        return iconResId;
    }

    public void setIconResId(int iconResId) {
        this.iconResId = iconResId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }
}