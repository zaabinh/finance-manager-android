package com.example.fintrack.app.settings.ui;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.fintrack.R;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.user.remote.NotificationSettingsResponse;
import com.example.fintrack.app.user.remote.UpdateNotificationSettingsRequest;
import com.example.fintrack.app.user.viewmodel.UserViewModel;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.util.Locale;

public class SettingNotificationActivity extends AppCompatActivity {
    private static final int[] THRESHOLDS = {50, 60, 70, 80, 90, 100};

    private SwitchMaterial switchNotifications;
    private SwitchMaterial switchDailyReminder;
    private SwitchMaterial switchLimitWarning;
    private SwitchMaterial switchBudgetWarning;
    private SwitchMaterial switchOtherNotifications;
    private View layoutTimePickerRow;
    private View rowBudgetThreshold;
    private View btnSave;
    private TextView tvReminderTime;
    private TextView tvBudgetThreshold;
    private UserViewModel viewModel;
    private int selectedHour = 20;
    private int selectedMinute;
    private int selectedThreshold = 80;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting_notification);
        viewModel = new ViewModelProvider(this).get(UserViewModel.class);
        initViews();
        setupListeners();
        setupObservers();
        viewModel.fetchNotificationSettings();
    }

    private void initViews() {
        switchNotifications = findViewById(R.id.switchNotifications);
        switchDailyReminder = findViewById(R.id.switchDailyReminder);
        switchLimitWarning = findViewById(R.id.switchLimitWarning);
        switchBudgetWarning = findViewById(R.id.switchBudgetWarning);
        switchOtherNotifications = findViewById(R.id.switchOtherNotifications);
        layoutTimePickerRow = findViewById(R.id.layoutTimePickerRow);
        rowBudgetThreshold = findViewById(R.id.rowBudgetThreshold);
        btnSave = findViewById(R.id.btnSaveNotificationSettings);
        tvReminderTime = findViewById(R.id.tvReminderTime);
        tvBudgetThreshold = findViewById(R.id.tvBudgetThreshold);
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        switchNotifications.setOnCheckedChangeListener((button, checked) -> updateEnabledState());
        switchDailyReminder.setOnCheckedChangeListener((button, checked) -> updateEnabledState());
        switchLimitWarning.setOnCheckedChangeListener((button, checked) -> updateEnabledState());
        layoutTimePickerRow.setOnClickListener(v -> showTimePicker());
        rowBudgetThreshold.setOnClickListener(v -> showThresholdPicker());
        btnSave.setOnClickListener(v -> save());
    }

    private void setupObservers() {
        viewModel.getNotificationSettingsResult().observe(this, resource -> {
            if (resource == null) return;
            if (resource.getStatus() == Resource.Status.SUCCESS && resource.getData() != null) {
                apply(resource.getData());
            } else if (resource.getStatus() == Resource.Status.ERROR) {
                showError(resource.getMessage());
            }
        });
        viewModel.getUpdateNotificationSettingsResult().observe(this, resource -> {
            if (resource == null) return;
            btnSave.setEnabled(resource.getStatus() != Resource.Status.LOADING);
            if (resource.getStatus() == Resource.Status.SUCCESS && resource.getData() != null) {
                apply(resource.getData());
                Toast.makeText(this, R.string.notification_settings_saved, Toast.LENGTH_SHORT).show();
            } else if (resource.getStatus() == Resource.Status.ERROR) {
                showError(resource.getMessage());
            }
        });
    }

    private void apply(NotificationSettingsResponse settings) {
        switchNotifications.setChecked(settings.isNotificationsEnabled());
        switchDailyReminder.setChecked(settings.isDailyReminderEnabled());
        switchLimitWarning.setChecked(settings.isBudgetAlertEnabled());
        switchBudgetWarning.setChecked(settings.isBudgetExceedAlertEnabled());
        switchOtherNotifications.setChecked(settings.isOtherNotificationsEnabled());
        selectedThreshold = settings.getBudgetAlertThresholdPercent();
        String time = settings.getDailyReminderTime();
        if (time != null && time.length() >= 5) {
            selectedHour = Integer.parseInt(time.substring(0, 2));
            selectedMinute = Integer.parseInt(time.substring(3, 5));
        }
        updateValues();
        updateEnabledState();
    }

    private void save() {
        viewModel.updateNotificationSettings(new UpdateNotificationSettingsRequest(
                switchNotifications.isChecked(), switchDailyReminder.isChecked(),
                String.format(Locale.ROOT, "%02d:%02d:00", selectedHour, selectedMinute),
                switchLimitWarning.isChecked(), selectedThreshold, switchBudgetWarning.isChecked(),
                switchOtherNotifications.isChecked()));
    }

    private void showTimePicker() {
        new TimePickerDialog(this, (view, hour, minute) -> {
            selectedHour = hour;
            selectedMinute = minute;
            updateValues();
        }, selectedHour, selectedMinute, true).show();
    }

    private void showThresholdPicker() {
        String[] labels = new String[THRESHOLDS.length];
        int selected = 0;
        for (int i = 0; i < THRESHOLDS.length; i++) {
            labels[i] = THRESHOLDS[i] + "%";
            if (THRESHOLDS[i] == selectedThreshold) selected = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.budget_alert_threshold)
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    selectedThreshold = THRESHOLDS[which];
                    updateValues();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void updateValues() {
        tvReminderTime.setText(String.format(Locale.getDefault(), "%02d:%02d", selectedHour, selectedMinute));
        tvBudgetThreshold.setText(String.format(Locale.getDefault(), "%d%%", selectedThreshold));
    }

    private void updateEnabledState() {
        boolean enabled = switchNotifications.isChecked();
        switchDailyReminder.setEnabled(enabled);
        switchLimitWarning.setEnabled(enabled);
        switchBudgetWarning.setEnabled(enabled);
        switchOtherNotifications.setEnabled(enabled);
        setEnabled(layoutTimePickerRow, enabled && switchDailyReminder.isChecked());
        setEnabled(rowBudgetThreshold, enabled && switchLimitWarning.isChecked());
    }

    private static void setEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.4f);
    }

    private void showError(String message) {
        Toast.makeText(this, message == null ? getString(R.string.error_server) : message, Toast.LENGTH_LONG).show();
    }
}
