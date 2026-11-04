package com.example.fintrack.app.wallet.model;

import java.io.Serializable;
import java.util.Locale;

// Lớp Model đại diện cho một chiếc ví tài chính của người dùng
// Implements Serializable giúp đối tượng này có thể truyền qua lại giữa các Activity thông qua Intent
public class WalletItem implements Serializable {
    private String id;           // Mã định danh duy nhất của ví
    private String name;         // Tên hiển thị của ví (ví dụ: "Techcombank", "Ví tiêu vặt")
    private String type;         // Loại ví: Tiền mặt, Ngân hàng, Ví điện tử, Tiết kiệm
    private String provider;     // Nhà cung cấp / Ngân hàng liên kết (ví dụ: Techcombank, Vietcombank, Momo)
    private double balance;      // Số dư hiện tại trong ví
    private String currency;     // Đơn vị tiền tệ (mặc định là "VND")
    private String note;         // Ghi chú thêm cho ví
    private boolean isActive;    // Trạng thái hoạt động của ví (true: đang hoạt động, false: vô hiệu hoá)

    // Constructor rỗng: Thiết lập giá trị mặc định cho đơn vị tiền tệ là "VND" và trạng thái là đang hoạt động
    public WalletItem() {
        this.currency = "VND";
        this.isActive = true;
    }

    // Constructor đầy đủ tham số để khởi tạo nhanh một đối tượng ví với các giá trị truyền vào
    public WalletItem(String id, String name, String type, String provider, double balance, String currency, String note, boolean isActive) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.provider = provider;
        this.balance = balance;
        this.currency = currency;
        this.note = note;
        this.isActive = isActive;
    }

    // Các phương thức Getter và Setter để truy xuất và cập nhật giá trị các thuộc tính

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

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    // Phương thức hỗ trợ định dạng số dư thành chuỗi tiền tệ dễ nhìn (có dấu chấm phân cách hàng nghìn và kèm chữ "đ")
    public String getFormattedBalance() {
        // Chuyển đổi số dư từ kiểu double sang long (bỏ phần lẻ thập phân)
        long intBalance = (long) balance;

        // Định dạng số theo chuẩn Đức (sử dụng dấu chấm `.` làm phân cách hàng nghìn, ví dụ: 15.736.000 đ)
        return String.format(Locale.GERMAN, "%,d đ", intBalance);
    }
}