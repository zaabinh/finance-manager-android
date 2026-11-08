package com.example.fintrack.app.setup.ui;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.fintrack.R;
import com.example.fintrack.app.settings.ui.SettingNotificationActivity;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Locale;

// Màn hình cài đặt thông báo và nhắc nhở (SetupNotificationActivity)
public class SetupNotificationActivity extends AppCompatActivity {

    private static final int NOTIF_PERMISSION_CODE = 102;

    public static final String EXTRA_REMINDER_TIME = "extra_reminder_time";
    public static final String EXTRA_NOTIF_ENABLED = "extra_notif_enabled";
    public static final String EXTRA_DAILY_REMINDER = "extra_daily_reminder";
    public static final String EXTRA_BUDGET_ALERT = "extra_budget_alert";
    public static final String EXTRA_BUDGET_THRESHOLD = "extra_budget_threshold";

    private ImageView ivBack;
    private SwitchMaterial switchEnableNotif;
    private SwitchMaterial switchDailyReminder;
    private LinearLayout llTimePickerRow;
    private TextView tvReminderTime;
    private SwitchMaterial switchBudgetAlert;
    private LinearLayout rowBudgetThreshold;
    private TextView tvBudgetThreshold;
    private int selectedThreshold = 80;
    private Button btnContinue;
    private TextView tvSkip;

    private int selectedHour = 20;
    private int selectedMinute = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_notification);

        initViews();
        setupListeners();
    }

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

    private void checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIF_PERMISSION_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIF_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Đã cấp quyền Thông báo", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Thông báo bị tắt. Bạn có thể bật lại trong Cài đặt hệ thống.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setupListeners() {
        ivBack.setOnClickListener(v -> finish());

        // Kiểm tra xin quyền khi người dùng gạt bật công tắc thông báo
        if (switchEnableNotif != null) {
            switchEnableNotif.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    checkAndRequestNotificationPermission();
                }
            });
        }

        if (switchDailyReminder != null) {
            switchDailyReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    checkAndRequestNotificationPermission();
                }
            });
        }

        llTimePickerRow.setOnClickListener(v -> showTimePickerDialog());
        rowBudgetThreshold.setOnClickListener(v -> showThresholdPicker());
        switchBudgetAlert.setOnCheckedChangeListener((b0, checked) -> rowBudgetThreshold.setEnabled(checked));
        rowBudgetThreshold.setEnabled(switchBudgetAlert.isChecked());
        btnContinue.setOnClickListener(v -> navigateNext());
        tvSkip.setOnClickListener(v -> navigateNext());
    }

    private void showTimePickerDialog() {
        TimePickerDialog dialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    selectedHour = hourOfDay;
                    selectedMinute = minute;
                    String formattedTime = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute);
                    tvReminderTime.setText(formattedTime);
                },
                selectedHour,
                selectedMinute,
                true
        );
        dialog.show();
    }

    private void navigateNext() {
        String formattedTime = tvReminderTime.getText().toString().trim();
        boolean notifEnabled = switchEnableNotif != null && switchEnableNotif.isChecked();

        Intent intent = new Intent(SetupNotificationActivity.this, SetupWalletActivity.class);
        intent.putExtras(getIntent());
        intent.putExtra(EXTRA_REMINDER_TIME, formattedTime);
        intent.putExtra(EXTRA_NOTIF_ENABLED, notifEnabled);
        intent.putExtra(EXTRA_DAILY_REMINDER, switchDailyReminder.isChecked());
        intent.putExtra(EXTRA_BUDGET_ALERT, switchBudgetAlert.isChecked());
        intent.putExtra(EXTRA_BUDGET_THRESHOLD, selectedThreshold);

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
