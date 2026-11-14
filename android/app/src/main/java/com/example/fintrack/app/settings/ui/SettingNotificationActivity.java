package com.example.fintrack.app.settings.ui;

import com.example.fintrack.app.core.network.ErrorMapper;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.fintrack.R;
import com.example.fintrack.databinding.ActivitySettingNotificationBinding;
import com.example.fintrack.app.FintrackApplication;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.user.remote.NotificationSettingsResponse;
import com.example.fintrack.app.user.remote.UpdateNotificationSettingsRequest;
import com.example.fintrack.app.user.viewmodel.UserViewModel;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.util.Locale;

public class SettingNotificationActivity extends AppCompatActivity {
    private static final int NOTIF_PERMISSION_CODE = 102;
    public static final int[] THRESHOLDS = {50, 60, 70, 80, 90, 100};

    private ActivitySettingNotificationBinding binding;

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
    private boolean settingsLoaded = false;
    private boolean saving = false;
    private static final String STATE_LOADED = "state_loaded";
    private static final String STATE_HOUR = "state_hour";
    private static final String STATE_MINUTE = "state_minute";
    private static final String STATE_THRESHOLD = "state_threshold";
    // Set after rotation once the server values were shown: the replayed fetch result must not wipe the user's edits.
    private boolean keepDraft = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingNotificationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        if (savedInstanceState != null && savedInstanceState.getBoolean(STATE_LOADED)) {
            keepDraft = true;
            selectedHour = savedInstanceState.getInt(STATE_HOUR, selectedHour);
            selectedMinute = savedInstanceState.getInt(STATE_MINUTE, selectedMinute);
            selectedThreshold = savedInstanceState.getInt(STATE_THRESHOLD, selectedThreshold);
        }
        viewModel = new ViewModelProvider(this, FintrackApplication.container(this).viewModelFactory()).get(UserViewModel.class);
        initViews();
        btnSave.setEnabled(false);
        setupListeners();
        setupObservers();
        viewModel.fetchNotificationSettings();
    }

    private void initViews() {
        switchNotifications = binding.switchNotifications;
        switchDailyReminder = binding.switchDailyReminder;
        switchLimitWarning = binding.switchLimitWarning;
        switchBudgetWarning = binding.switchBudgetWarning;
        switchOtherNotifications = binding.switchOtherNotifications;
        layoutTimePickerRow = binding.layoutTimePickerRow;
        rowBudgetThreshold = binding.rowBudgetThreshold;
        btnSave = binding.btnSaveNotificationSettings;
        tvReminderTime = binding.tvReminderTime;
        tvBudgetThreshold = binding.tvBudgetThreshold;
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        switchNotifications.setOnCheckedChangeListener((button, checked) -> {
            updateEnabledState();
            if (button.isPressed() && checked) checkAndRequestNotificationPermission();
        });
        switchDailyReminder.setOnCheckedChangeListener((button, checked) -> {
            updateEnabledState();
            if (button.isPressed() && checked) checkAndRequestNotificationPermission();
        });
        switchLimitWarning.setOnCheckedChangeListener((button, checked) -> {
            updateEnabledState();
            if (button.isPressed() && checked) checkAndRequestNotificationPermission();
        });
        switchBudgetWarning.setOnCheckedChangeListener((button, checked) -> {
            if (button.isPressed() && checked) checkAndRequestNotificationPermission();
        });
        switchOtherNotifications.setOnCheckedChangeListener((button, checked) -> {
            if (button.isPressed() && checked) checkAndRequestNotificationPermission();
        });
        layoutTimePickerRow.setOnClickListener(v -> showTimePicker());
        rowBudgetThreshold.setOnClickListener(v -> showThresholdPicker());
        btnSave.setOnClickListener(v -> save());
    }

    private void setupObservers() {
        viewModel.getNotificationSettingsResult().observe(this, resource -> {
            if (resource == null) return;
            if (resource.getStatus() == Resource.Status.LOADING) {
                settingsLoaded = false;
                btnSave.setEnabled(false);
            } else if (resource.getStatus() == Resource.Status.SUCCESS && resource.getData() != null) {
                if (keepDraft) {
                    keepDraft = false;
                    updateValues();
                    updateEnabledState();
                } else {
                    apply(resource.getData());
                }
                settingsLoaded = true;
                btnSave.setEnabled(!saving);
            } else {
                settingsLoaded = false;
                btnSave.setEnabled(false);
                if (resource.getStatus() == Resource.Status.ERROR) showError(ErrorMapper.text(this, resource.getError()));
            }
        });
        viewModel.getUpdateNotificationSettingsResult().observe(this, resource -> {
            if (resource == null) return;
            saving = resource.getStatus() == Resource.Status.LOADING;
            btnSave.setEnabled(settingsLoaded && !saving);
            if (resource.getStatus() == Resource.Status.SUCCESS && resource.getData() != null) {
                apply(resource.getData());
                Toast.makeText(this, R.string.notification_settings_saved, Toast.LENGTH_SHORT).show();
            } else if (resource.getStatus() == Resource.Status.ERROR) {
                showError(ErrorMapper.text(this, resource.getError()));
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
        if (!settingsLoaded || saving) return;
        saving = true;
        btnSave.setEnabled(false);
        if (switchNotifications.isChecked()) checkAndRequestNotificationPermission();
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

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_LOADED, settingsLoaded);
        outState.putInt(STATE_HOUR, selectedHour);
        outState.putInt(STATE_MINUTE, selectedMinute);
        outState.putInt(STATE_THRESHOLD, selectedThreshold);
    }

    private void showError(String message) {
        Toast.makeText(this, message == null ? getString(R.string.error_server) : message, Toast.LENGTH_LONG).show();
    }

    private void checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIF_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIF_PERMISSION_CODE) {
            int message = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    ? R.string.notif_permission_granted
                    : R.string.notif_permission_denied;
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }
}
