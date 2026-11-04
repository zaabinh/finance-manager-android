package com.example.fintrack.app.setup.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fintrack.R;

// Màn hình thiết lập ví tiền/tài khoản (SetupWalletActivity) trong luồng cài đặt
public class SetupWalletActivity extends AppCompatActivity {

    // Khai báo các thành phần giao diện (UI)
    private ImageView ivBack;          // Nút Quay lại
    private TextView tvAddWallet;      // Thẻ/nút bấm để thêm ví mới
    private Button btnCompleteSetup;   // Nút hoàn tất cài đặt

    // Lưu trữ thông tin nhận được từ các màn hình trước (với giá trị mặc định phòng trường hợp không có dữ liệu)
    private String displayName = "Minh Anh";
    private String reminderTime = "20:00";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_wallet); // Gắn giao diện XML vào Activity

        // Lấy dữ liệu (Tên và Giờ nhắc nhở) truyền từ các màn hình trước sang
        if (getIntent() != null) {
            if (getIntent().hasExtra(SetupBasicInfoActivity.EXTRA_DISPLAY_NAME)) {
                displayName = getIntent().getStringExtra(SetupBasicInfoActivity.EXTRA_DISPLAY_NAME);
            }
            if (getIntent().hasExtra(SetupNotificationActivity.EXTRA_REMINDER_TIME)) {
                reminderTime = getIntent().getStringExtra(SetupNotificationActivity.EXTRA_REMINDER_TIME);
            }
        }

        initViews();       // Ánh xạ các thành phần giao diện
        setupListeners();  // Gán sự kiện bấm cho các nút
    }

    // Ánh xạ ID từ file XML sang biến Java
    private void initViews() {
        ivBack = findViewById(R.id.ivBack);
        tvAddWallet = findViewById(R.id.tvAddWallet);
        btnCompleteSetup = findViewById(R.id.btnCompleteSetup);
    }

    // Thiết lập xử lý sự kiện bấm cho các nút
    private void setupListeners() {
        // Nút Back: Đóng màn hình hiện tại để quay lại màn hình thông báo
        ivBack.setOnClickListener(v -> finish());

        // Bấm vào nút Thêm ví: Hiển thị thông báo nhỏ (Toast) nhắc người dùng có thể thêm ví sau trong app
        tvAddWallet.setOnClickListener(v ->
                Toast.makeText(this, "Tùy chọn thêm ví có thể cài đặt thêm trong ứng dụng", Toast.LENGTH_SHORT).show()
        );

        // Nút Hoàn tất: Chuyển sang màn hình xác nhận hoàn tất (SetupCompleteActivity)
        btnCompleteSetup.setOnClickListener(v -> {
            Intent intent = new Intent(SetupWalletActivity.this, SetupCompleteActivity.class);

            // Tiếp tục chuyển tiếp Tên và Giờ nhắc nhở sang màn hình cuối
            intent.putExtra(SetupBasicInfoActivity.EXTRA_DISPLAY_NAME, displayName);
            intent.putExtra(SetupNotificationActivity.EXTRA_REMINDER_TIME, reminderTime);

            startActivity(intent);
        });
    }
}