package com.example.fintrack.app.settings.ui;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fintrack.R;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Locale;

public class SettingNotificationActivity extends AppCompatActivity {

    private TextView tvReminderTime;
    private int selectedHour = 20;
    private int selectedMinute = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting_notification);

        initViews();
        setupListeners();
    }

    private void initViews() {
        tvReminderTime = findViewById(R.id.tvReminderTime);
        SwitchMaterial switchDailyReminder = findViewById(R.id.switchDailyReminder);

        if (switchDailyReminder != null) {
            switchDailyReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
                int msgRes = isChecked ? R.string.notif_reminder_on : R.string.notif_reminder_off;
                Toast.makeText(this, msgRes, Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        tvReminderTime.setOnClickListener(v -> showTimePickerDialog());
    }

    private void showTimePickerDialog() {
        TimePickerDialog timePickerDialog = new TimePickerDialog(
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
        timePickerDialog.show();
    }
}
