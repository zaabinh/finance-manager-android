package com.example.fintrack.app.analytics.model;

import java.io.Serializable;

// Lớp mô hình (Model) đại diện cho một danh mục chi tiêu cụ thể (Ví dụ: Ăn uống, Nhà ở,...)
// Implement Serializable để có thể truyền đối tượng này qua lại giữa các thành phần
public class AnalyticsCategoryItem implements Serializable {

    private String id;              // Mã định danh danh mục (Ví dụ: "c1", "c2")
    private String name;            // Tên danh mục (Ví dụ: "Ăn uống", "Nhà ở")
    private int percentage;         // Tỷ lệ phần trăm chiếm trong tổng chi tiêu (Ví dụ: 25)
    private String amountFormatted; // Số tiền đã được định dạng dạng chuỗi (Ví dụ: "2.500.000 đ")
    private int iconResId;          // Mã định danh tài nguyên hình ảnh/icon của danh mục (Ví dụ: R.drawable.ic_coffee)

    // Hàm khởi tạo mặc định (Constructor không tham số)
    public AnalyticsCategoryItem() {}

    // Hàm khởi tạo đầy đủ tham số để dễ dàng khởi tạo nhanh đối tượng
    public AnalyticsCategoryItem(String id, String name, int percentage, String amountFormatted, int iconResId) {
        this.id = id;
        this.name = name;
        this.percentage = percentage;
        this.amountFormatted = amountFormatted;
        this.iconResId = iconResId;
    }

    // --- Các hàm Getter và Setter để truy xuất và cập nhật giá trị ---

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

    public int getPercentage() {
        return percentage;
    }

    public void setPercentage(int percentage) {
        this.percentage = percentage;
    }

    public String getAmountFormatted() {
        return amountFormatted;
    }

    public void setAmountFormatted(String amountFormatted) {
        this.amountFormatted = amountFormatted;
    }

    public int getIconResId() {
        return iconResId;
    }

    public void setIconResId(int iconResId) {
        this.iconResId = iconResId;
    }
}