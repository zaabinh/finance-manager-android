package com.example.fintrack.app.setup.ui;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.fintrack.R;
import com.example.fintrack.app.settings.ui.SettingNotificationActivity;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Locale;

// Màn hình cài đặt thông báo và nhắc nhở (SetupNotificationActivity)
public class SetupNotificationActivity extends AppCompatActivity {

    // Các khóa (Key) để truyền dữ liệu thời gian nhắc nhở và trạng thái bật/tắt thông báo sang màn hình tiếp theo
    public static final String EXTRA_REMINDER_TIME = "extra_reminder_time";
    public static final String EXTRA_NOTIF_ENABLED = "extra_notif_enabled";
    public static final String EXTRA_DAILY_REMINDER = "extra_daily_reminder";
    public static final String EXTRA_BUDGET_ALERT = "extra_budget_alert";
    public static final String EXTRA_BUDGET_THRESHOLD = "extra_budget_threshold";

    // Khai báo các thành phần giao diện (UI)
    private ImageView ivBack;                 // Nút Quay lại
    private SwitchMaterial switchEnableNotif;  // Công tắc bật/tắt toàn bộ thông báo
    private SwitchMaterial switchDailyReminder;// Công tắc bật/tắt nhắc nhở hàng ngày
    private LinearLayout llTimePickerRow;     // Dòng chứa thời gian (bấm vào để chọn giờ)
    private TextView tvReminderTime;          // Thẻ hiển thị giờ đã chọn (ví dụ: 20:00)
    private SwitchMaterial switchBudgetAlert;  // Công tắc bật/tắt cảnh báo ngân sách
    private LinearLayout rowBudgetThreshold;  // Dòng chọn ngưỡng cảnh báo ngân sách
    private TextView tvBudgetThreshold;       // Ngưỡng đã chọn (ví dụ: 80%)
    private int selectedThreshold = 80;       // Ngưỡng mặc định
    private Button btnContinue;               // Nút "Tiếp tục"
    private TextView tvSkip;                  // Nút "Bỏ qua"

    // Lưu trữ thông tin cài đặt mặc định
    private int selectedHour = 20;             // Giờ mặc định: 20h
    private int selectedMinute = 0;            // Phút mặc định: 00

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_notification); // Gắn giao diện XML vào Activity

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
        rowBudgetThreshold = findViewById(R.id.rowBudgetThreshold);
        tvBudgetThreshold = findViewById(R.id.tvBudgetThreshold);
        btnContinue = findViewById(R.id.btnContinue);
        tvSkip = findViewById(R.id.tvSkip);
    }

    // Thiết lập các sự kiện khi người dùng tương tác
    private void setupListeners() {
        // Nút Back: Đóng màn hình hiện tại để quay lại bước trước
        ivBack.setOnClickListener(v -> finish());

        // Dòng chọn thời gian: Bấm vào dòng này sẽ hiển thị bảng đồng hồ chọn giờ
        llTimePickerRow.setOnClickListener(v -> showTimePickerDialog());

        // Ngưỡng chỉ có ý nghĩa khi bật cảnh báo ngân sách
        rowBudgetThreshold.setOnClickListener(v -> showThresholdPicker());
        switchBudgetAlert.setOnCheckedChangeListener((b, checked) -> rowBudgetThreshold.setEnabled(checked));

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

        // Giữ lại toàn bộ input bước trước (tên, múi giờ) rồi thêm cài đặt thông báo
        intent.putExtras(getIntent());
        intent.putExtra(EXTRA_REMINDER_TIME, formattedTime);
        intent.putExtra(EXTRA_NOTIF_ENABLED, notifEnabled);
        intent.putExtra(EXTRA_DAILY_REMINDER, switchDailyReminder.isChecked());
        intent.putExtra(EXTRA_BUDGET_ALERT, switchBudgetAlert.isChecked());
        intent.putExtra(EXTRA_BUDGET_THRESHOLD, selectedThreshold);

        // Khởi chạy màn hình mới
        startActivity(intent);
    }

    // Hộp thoại chọn ngưỡng cảnh báo, dùng chung danh sách với màn Cài đặt thông báo
    private void showThresholdPicker() {
        int[] thresholds = SettingNotificationActivity.THRESHOLDS;
        String[] labels = new String[thresholds.length];
        int selected = 0;
        for (int i = 0; i < thresholds.length; i++) {
            labels[i] = thresholds[i] + "%";
            if (thresholds[i] == selectedThreshold) selected = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.budget_alert_threshold)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    selectedThreshold = thresholds[which];
                    tvBudgetThreshold.setText(labels[which]);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }
}
