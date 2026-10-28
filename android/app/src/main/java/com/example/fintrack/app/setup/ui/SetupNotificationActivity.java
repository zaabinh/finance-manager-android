package com.example.fintrack.app.setup.ui;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fintrack.R;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Locale;

// Màn hình cài đặt thông báo và nhắc nhở (SetupNotificationActivity)
public class SetupNotificationActivity extends AppCompatActivity {

    // Các khóa (Key) để truyền dữ liệu thời gian nhắc nhở và trạng thái bật/tắt thông báo sang màn hình tiếp theo
    public static final String EXTRA_REMINDER_TIME = "extra_reminder_time";
    public static final String EXTRA_NOTIF_ENABLED = "extra_notif_enabled";

    // Khai báo các thành phần giao diện (UI)
    private ImageView ivBack;                 // Nút Quay lại
    private SwitchMaterial switchEnableNotif;  // Công tắc bật/tắt toàn bộ thông báo
    private SwitchMaterial switchDailyReminder;// Công tắc bật/tắt nhắc nhở hàng ngày
    private LinearLayout llTimePickerRow;     // Dòng chứa thời gian (bấm vào để chọn giờ)
    private TextView tvReminderTime;          // Thẻ hiển thị giờ đã chọn (ví dụ: 20:00)
    private SwitchMaterial switchBudgetAlert;  // Công tắc bật/tắt cảnh báo ngân sách
    private Button btnContinue;               // Nút "Tiếp tục"
    private TextView tvSkip;                  // Nút "Bỏ qua"

    // Lưu trữ thông tin cài đặt mặc định
    private String displayName = "Minh Anh";   // Tên người dùng (nhận từ màn hình trước)
    private int selectedHour = 20;             // Giờ mặc định: 20h
    private int selectedMinute = 0;            // Phút mặc định: 00

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_notification); // Gắn giao diện XML vào Activity

        // Lấy tên người dùng được truyền sang từ màn hình SetupBasicInfoActivity (nếu có)
        if (getIntent() != null && getIntent().hasExtra(SetupBasicInfoActivity.EXTRA_DISPLAY_NAME)) {
            displayName = getIntent().getStringExtra(SetupBasicInfoActivity.EXTRA_DISPLAY_NAME);
        }

        initViews();       // Ánh xạ các thành phần giao diện
        setupListeners();  // Gán sự kiện bấm cho các nút/dòng
    }

    // Ánh xạ ID từ file XML sang biến Java
    private void initViews() {
        ivBack = findViewById(R.id.ivBack);
        switchEnableNotif = findViewById(R.id.switchEnableNotif);
        switchDailyReminder = findViewById(R.id.switchDailyReminder);
        llTimePickerRow = findViewById(R.id.llTimePickerRow);
        tvReminderTime = findViewById(R.id.tvReminderTime);
        switchBudgetAlert = findViewById(R.id.switchBudgetAlert);
        btnContinue = findViewById(R.id.btnContinue);
        tvSkip = findViewById(R.id.tvSkip);
    }

    // Thiết lập các sự kiện khi người dùng tương tác
    private void setupListeners() {
        // Nút Back: Đóng màn hình hiện tại để quay lại bước trước
        ivBack.setOnClickListener(v -> finish());

        // Dòng chọn thời gian: Bấm vào dòng này sẽ hiển thị bảng đồng hồ chọn giờ
        llTimePickerRow.setOnClickListener(v -> showTimePickerDialog());

        // Nút Tiếp tục: Chuyển sang bước tiếp theo
        btnContinue.setOnClickListener(v -> navigateNext());

        // Nút Bỏ qua: Cũng chuyển sang bước tiếp theo (giữ cài đặt mặc định)
        tvSkip.setOnClickListener(v -> navigateNext());
    }

    // Hộp thoại (Dialog) chọn thời gian (giờ:phút)
    private void showTimePickerDialog() {
        TimePickerDialog dialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    // Lưu lại giờ và phút người dùng đã chọn
                    selectedHour = hourOfDay;
                    selectedMinute = minute;
                    // Định dạng thời gian theo kiểu 2 chữ số (ví dụ: 08:05 hoặc 20:00)
                    String formattedTime = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute);
                    // Hiển thị chuỗi thời gian vừa chọn lên TextView
                    tvReminderTime.setText(formattedTime);
                },
                selectedHour,
                selectedMinute,
                true // Sử dụng định dạng 24 giờ (true)
        );
        dialog.show(); // Hiển thị đồng hồ chọn giờ
    }

    // Hàm chuyển sang màn hình tiếp theo (SetupWalletActivity)
    private void navigateNext() {
        // Lấy thời gian hiển thị hiện tại và trạng thái của công tắc bật thông báo
        String formattedTime = tvReminderTime.getText().toString().trim();
        boolean notifEnabled = switchEnableNotif.isChecked();

        // Tạo Intent chuyển đến màn hình tạo Ví (SetupWalletActivity)
        Intent intent = new Intent(SetupNotificationActivity.this, SetupWalletActivity.class);

        // Đóng gói dữ liệu (Tên, Giờ nhắc nhở, Trạng thái bật thông báo) để chuyển đi
        intent.putExtra(SetupBasicInfoActivity.EXTRA_DISPLAY_NAME, displayName);
        intent.putExtra(EXTRA_REMINDER_TIME, formattedTime);
        intent.putExtra(EXTRA_NOTIF_ENABLED, notifEnabled);

        // Khởi chạy màn hình mới
        startActivity(intent);
    }
}