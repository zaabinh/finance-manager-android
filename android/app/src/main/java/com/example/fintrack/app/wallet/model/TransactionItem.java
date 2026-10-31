package com.example.fintrack.app.wallet.model;

import java.io.Serializable;
import java.util.Locale;

// Lớp Model đại diện cho một giao dịch tài chính (thu hoặc chi)
// Implements Serializable để có thể dễ dàng truyền đối tượng này qua lại giữa các Activity/Fragment qua Intent
public class TransactionItem implements Serializable {
    private String id;           // Mã định danh giao dịch
    private String title;        // Tiêu đề giao dịch (ví dụ: "Highlands Coffee", "Siêu thị WinMart")
    private String subtitle;     // Thông tin phụ (ví dụ: "Techcombank • Hôm qua, 15:45")
    private double amount;       // Số tiền giao dịch
    private boolean isIncome;    // Phân loại: true là tiền vào (thu), false là tiền ra (chi)
    private int iconResId;       // ID tài nguyên hình ảnh (icon) minh hoạ cho giao dịch

    // Constructor rỗng (bắt buộc cho một số thư viện chuyển đổi như Firebase hoặc Gson)
    public TransactionItem() {
    }

    // Constructor đầy đủ tham số để khởi tạo nhanh một đối tượng giao dịch
    public TransactionItem(String id, String title, String subtitle, double amount, boolean isIncome, int iconResId) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.amount = amount;
        this.isIncome = isIncome;
        this.iconResId = iconResId;
    }

    // Các phương thức Getter và Setter để truy xuất và cập nhật giá trị cho các thuộc tính

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public boolean isIncome() {
        return isIncome;
    }

    public void setIncome(boolean income) {
        isIncome = income;
    }

    public int getIconResId() {
        return iconResId;
    }

    public void setIconResId(int iconResId) {
        this.iconResId = iconResId;
    }

    // Phương thức hỗ trợ định dạng lại số tiền thành chuỗi dễ đọc (có dấu phân cách hàng nghìn và dấu hiệu + / -)
    public String getFormattedAmount() {
        // Lấy giá trị tuyệt đối của số tiền và chuyển sang kiểu long (bỏ phần thập phân)
        long intAmount = (long) Math.abs(amount);

        // Định dạng số theo chuẩn của Đức (sử dụng dấu chấm `.` làm phân cách hàng nghìn, ví dụ: 25.000.000)
        String formattedNumber = String.format(Locale.GERMAN, "%,d", intAmount);

        // Trả về chuỗi hoàn chỉnh kèm ký hiệu: "+đ" nếu là thu nhập, "-đ" nếu là chi tiêu
        return (isIncome ? "+đ" : "-đ") + formattedNumber;
    }
}