package com.example.fintrack.app.settings.ui;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fintrack.R;

import java.util.Locale;

public class SettingNotificationActivity extends AppCompatActivity {

    // Khai báo các thành phần giao diện (UI)
    private ImageButton btnBack;
    private Switch switchDailyReminder;
    private TextView tvReminderTime;
    private Switch switchLimitWarning;
    private Switch switchBudgetWarning;

    // Biến lưu trữ giờ và phút mặc định cho đồng hồ chọn thời gian
    private int selectedHour = 20;
    private int selectedMinute = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting_notification); // Gắn file XML tương ứng

        initViews();       // Ánh xạ ID từ XML
        setupListeners();  // Gán sự kiện tương tác
    }

    // Ánh xạ các thành phần từ XML sang Java
    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        switchDailyReminder = findViewById(R.id.switchDailyReminder);
        tvReminderTime = findViewById(R.id.tvReminderTime);
        switchLimitWarning = findViewById(R.id.switchLimitWarning);
        switchBudgetWarning = findViewById(R.id.switchBudgetWarning);
    }

    // Thiết lập sự kiện bấm nút, bật/tắt switch và chọn giờ
    private void setupListeners() {
        // 1. Nút quay lại: Đóng màn hình hiện tại
        btnBack.setOnClickListener(v -> finish());

        // 2. Bấm vào khung thời gian nhắc nhở -> Hiển thị hộp thoại chọn giờ (TimePickerDialog)
        tvReminderTime.setOnClickListener(v -> showTimePickerDialog());

        // 3. Lắng nghe trạng thái bật/tắt của các Switch (nếu cần lưu trữ hoặc xử lý logic)
        switchDailyReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            String status = isChecked ? "Đã bật nhắc nhở hằng ngày" : "Đã tắt nhắc nhở hằng ngày";
            Toast.makeText(this, status, Toast.LENGTH_SHORT).show();
        });

        switchLimitWarning.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Xử lý khi bật/tắt cảnh báo sắp chạm hạn mức
        });

        switchBudgetWarning.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Xử lý khi bật/tắt cảnh báo vượt ngân sách
        });
    }

    // Hộp thoại chọn giờ 24h
    private void showTimePickerDialog() {
        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    selectedHour = hourOfDay;
                    selectedMinute = minute;
                    // Định dạng lại chuỗi giờ:phút (ví dụ: 20:05)
                    String formattedTime = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute);
                    tvReminderTime.setText(formattedTime);
                },
                selectedHour,
                selectedMinute,
                true // Sử dụng định dạng 24 giờ
        );
        timePickerDialog.show();
    }
}