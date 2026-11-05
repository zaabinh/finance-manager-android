package com.example.fintrack.app.settings.ui;

import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fintrack.R;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Locale;

/**
 * Màn hình Cấu hình Thông báo (SettingNotificationActivity):
 * Tự động lưu và khôi phục trạng thái bật/tắt nhắc nhở hằng ngày, giờ nhắc, cảnh báo hạn mức.
 */
public class SettingNotificationActivity extends AppCompatActivity {

    private static final String PREF_NOTIF_SETTINGS = "fintrack_notif_config";
    private static final String KEY_DAILY_ENABLED = "daily_reminder_enabled";
    private static final String KEY_REMINDER_HOUR = "reminder_hour";
    private static final String KEY_REMINDER_MINUTE = "reminder_minute";
    private static final String KEY_NEAR_LIMIT_ENABLED = "near_limit_enabled";
    private static final String KEY_OVER_BUDGET_ENABLED = "over_budget_enabled";

    private SwitchMaterial switchDailyReminder;
    private SwitchMaterial switchLimitWarning;
    private SwitchMaterial switchBudgetWarning;
    private View layoutTimePickerRow;
    private TextView tvReminderTime;

    private int selectedHour = 20;
    private int selectedMinute = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting_notification);

        initViews();
        loadSavedSettings();
        setupListeners();
    }

    private void initViews() {
        switchDailyReminder = findViewById(R.id.switchDailyReminder);
        switchLimitWarning = findViewById(R.id.switchLimitWarning);
        switchBudgetWarning = findViewById(R.id.switchBudgetWarning);
        layoutTimePickerRow = findViewById(R.id.layoutTimePickerRow);
        tvReminderTime = findViewById(R.id.tvReminderTime);
    }

    private void loadSavedSettings() {
        SharedPreferences prefs = getSharedPreferences(PREF_NOTIF_SETTINGS, Context.MODE_PRIVATE);

        boolean dailyEnabled = prefs.getBoolean(KEY_DAILY_ENABLED, true);
        boolean nearLimitEnabled = prefs.getBoolean(KEY_NEAR_LIMIT_ENABLED, true);
        boolean overBudgetEnabled = prefs.getBoolean(KEY_OVER_BUDGET_ENABLED, true);

        selectedHour = prefs.getInt(KEY_REMINDER_HOUR, 20);
        selectedMinute = prefs.getInt(KEY_REMINDER_MINUTE, 0);

        if (switchDailyReminder != null) switchDailyReminder.setChecked(dailyEnabled);
        if (switchLimitWarning != null) switchLimitWarning.setChecked(nearLimitEnabled);
        if (switchBudgetWarning != null) switchBudgetWarning.setChecked(overBudgetEnabled);

        updateReminderTimeDisplay();
        updateSubOptionsEnabledState(dailyEnabled);
    }

    private void updateReminderTimeDisplay() {
        String formattedTime = String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute);
        if (tvReminderTime != null) {
            tvReminderTime.setText(formattedTime);
        }
    }

    private void updateSubOptionsEnabledState(boolean enabled) {
        if (layoutTimePickerRow != null) layoutTimePickerRow.setAlpha(enabled ? 1.0f : 0.4f);
        if (tvReminderTime != null) tvReminderTime.setEnabled(enabled);
        if (switchLimitWarning != null) switchLimitWarning.setEnabled(enabled);
        if (switchBudgetWarning != null) switchBudgetWarning.setEnabled(enabled);
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        if (switchDailyReminder != null) {
            switchDailyReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
                updateSubOptionsEnabledState(isChecked);
                getSharedPreferences(PREF_NOTIF_SETTINGS, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_DAILY_ENABLED, isChecked)
                        .apply();

                int msgRes = isChecked ? R.string.notif_reminder_on : R.string.notif_reminder_off;
                Toast.makeText(this, msgRes, Toast.LENGTH_SHORT).show();
            });
        }

        if (switchLimitWarning != null) {
            switchLimitWarning.setOnCheckedChangeListener((buttonView, isChecked) ->
                    getSharedPreferences(PREF_NOTIF_SETTINGS, Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean(KEY_NEAR_LIMIT_ENABLED, isChecked)
                            .apply()
            );
        }

        if (switchBudgetWarning != null) {
            switchBudgetWarning.setOnCheckedChangeListener((buttonView, isChecked) ->
                    getSharedPreferences(PREF_NOTIF_SETTINGS, Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean(KEY_OVER_BUDGET_ENABLED, isChecked)
                            .apply()
            );
        }

        if (tvReminderTime != null) {
            tvReminderTime.setOnClickListener(v -> showTimePickerDialog());
        }
    }

    private void showTimePickerDialog() {
        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    selectedHour = hourOfDay;
                    selectedMinute = minute;
                    updateReminderTimeDisplay();

                    getSharedPreferences(PREF_NOTIF_SETTINGS, Context.MODE_PRIVATE)
                            .edit()
                            .putInt(KEY_REMINDER_HOUR, selectedHour)
                            .putInt(KEY_REMINDER_MINUTE, selectedMinute)
                            .apply();

                    Toast.makeText(this, "Đã lưu giờ nhắc: " + tvReminderTime.getText(), Toast.LENGTH_SHORT).show();
                },
                selectedHour,
                selectedMinute,
                true
        );
        timePickerDialog.show();
    }
}
