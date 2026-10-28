package com.example.fintrack.app.setup.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fintrack.R;
import com.example.fintrack.app.core.session.SessionManager;
import com.example.fintrack.app.home.ui.HomeDashboardActivity;

// Màn hình hoàn tất cài đặt (SetupCompleteActivity)
public class SetupCompleteActivity extends AppCompatActivity {

    // Khai báo các thành phần giao diện (UI)
    private TextView tvSummaryName;         // Hiển thị tên người dùng
    private TextView tvSummaryReminderTime; // Hiển thị giờ nhắc nhở
    private Button btnEnterApp;             // Nút bấm để vào ứng dụng chính

    // Dữ liệu mặc định (nếu không nhận được dữ liệu từ màn hình trước)
    private String displayName = "Minh Anh";
    private String reminderTime = "20:00";
    private SessionManager sessionManager;  // Quản lý trạng thái đăng nhập và dữ liệu phiên làm việc

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_complete); // Gắn giao diện XML vào Activity

        // Khởi tạo SessionManager để lưu trữ thông tin
        sessionManager = SessionManager.getInstance(getApplicationContext());

        // Lấy dữ liệu (Tên và Giờ nhắc nhở) được truyền từ các màn hình thiết lập trước đó
        if (getIntent() != null) {
            if (getIntent().hasExtra(SetupBasicInfoActivity.EXTRA_DISPLAY_NAME)) {
                displayName = getIntent().getStringExtra(SetupBasicInfoActivity.EXTRA_DISPLAY_NAME);
            }
            if (getIntent().hasExtra(SetupNotificationActivity.EXTRA_REMINDER_TIME)) {
                reminderTime = getIntent().getStringExtra(SetupNotificationActivity.EXTRA_REMINDER_TIME);
            }
        }

        initViews();    // Ánh xạ các thành phần giao diện
        displayData();  // Hiển thị dữ liệu lên màn hình
        setupListeners(); // Gán sự kiện bấm nút
    }

    // Ánh xạ ID từ file XML sang biến trong Java
    private void initViews() {
        tvSummaryName = findViewById(R.id.tvSummaryName);
        tvSummaryReminderTime = findViewById(R.id.tvSummaryReminderTime);
        btnEnterApp = findViewById(R.id.btnEnterApp);
    }

    // Đưa dữ liệu tên và thời gian lên TextView để người dùng xem lại
    private void displayData() {
        tvSummaryName.setText(displayName);
        tvSummaryReminderTime.setText(reminderTime);
    }

    // Xử lý sự kiện khi người dùng bấm nút "Vào ứng dụng"
    private void setupListeners() {
        btnEnterApp.setOnClickListener(v -> {
            // 1. Đánh dấu trạng thái là đã hoàn tất cài đặt (Setup)
            sessionManager.setSetupCompleted(true);

            // 2. Lưu thông tin hồ sơ người dùng vào SessionManager
            sessionManager.saveUserProfile(
                    sessionManager.getUserId(),
                    sessionManager.getUserEmail(),
                    displayName
            );

            // 3. Chuyển hướng sang màn hình chính (HomeDashboardActivity)
            Intent intent = new Intent(SetupCompleteActivity.this, HomeDashboardActivity.class);
            // Xóa sạch lịch sử màn hình cũ để người dùng không bấm nút Back quay lại trang cài đặt được nữa
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish(); // Đóng màn hình hoàn tất lại
        });
    }
}