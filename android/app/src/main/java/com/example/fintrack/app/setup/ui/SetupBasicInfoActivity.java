package com.example.fintrack.app.setup.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fintrack.R;
import com.example.fintrack.databinding.ActivitySetupBasicInfoBinding;
import com.example.fintrack.app.settings.ui.SettingUserActivity;

import java.util.Arrays;
import java.util.TimeZone;

// Màn hình nhập thông tin cơ bản (Tên người dùng) trong luồng cài đặt
public class SetupBasicInfoActivity extends AppCompatActivity {

    // Khóa (Key) để đóng gói và gửi tên người dùng sang màn hình tiếp theo
    public static final String EXTRA_DISPLAY_NAME = "extra_display_name";
    public static final String EXTRA_TIME_ZONE = "extra_time_zone";
    private static final String STATE_TIME_ZONE = "state_time_zone";

    private ActivitySetupBasicInfoBinding binding;

    // Khai báo các thành phần giao diện (UI)
    private ImageView ivBack;       // Nút quay lại (mũi tên back)
    private EditText etDisplayName;  // Ô nhập tên hiển thị
    private Button btnContinue;     // Nút "Tiếp tục"
    private TextView tvTimezoneValue; // Ô chọn múi giờ
    private String selectedTimeZone = TimeZone.getDefault().getID(); // Mặc định theo máy

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySetupBasicInfoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot()); // Gắn giao diện XML vào Activity

        if (savedInstanceState != null)
            selectedTimeZone = savedInstanceState.getString(STATE_TIME_ZONE, selectedTimeZone);

        initViews();       // Ánh xạ các thành phần giao diện
        setupListeners();  // Gán sự kiện bấm nút
    }

    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_TIME_ZONE, selectedTimeZone);
    }

    // Ánh xạ ID từ file XML sang biến trong Java
    private void initViews() {
        ivBack = binding.ivBack;
        etDisplayName = binding.etDisplayName;
        btnContinue = binding.btnContinue;
        tvTimezoneValue = binding.tvTimezoneValue;
        showTimezone();
    }

    // Xử lý các sự kiện bấm nút
    private void setupListeners() {
        // Nút Back: Bấm vào thì đóng màn hình hiện tại (trở về màn hình trước)
        ivBack.setOnClickListener(v -> finish());
        tvTimezoneValue.setOnClickListener(v -> showTimezonePicker());

        // Nút Tiếp tục: Xử lý thông tin tên và chuyển sang bước tiếp theo
        btnContinue.setOnClickListener(v -> {
            // Lấy chuỗi nhập từ ô EditText và cắt bỏ khoảng trắng thừa ở 2 đầu
            String name = etDisplayName.getText().toString().trim();

            // Kiểm tra validation: Bắt buộc người dùng nhập tên hiển thị
            if (TextUtils.isEmpty(name)) {
                etDisplayName.setError(getString(R.string.error_name_empty));
                etDisplayName.requestFocus();
                return;
            }

            // Tạo Intent chuyển hướng sang màn hình thiết lập thông báo (SetupNotificationActivity)
            Intent intent = new Intent(SetupBasicInfoActivity.this, SetupNotificationActivity.class);

            // Đính kèm dữ liệu tên người dùng vừa lấy được vào Intent
            intent.putExtra(EXTRA_DISPLAY_NAME, name);
            intent.putExtra(EXTRA_TIME_ZONE, selectedTimeZone);

            // Mở màn hình tiếp theo
            startActivity(intent);
        });
    }

    private int selectedTimezoneIndex() {
        return Arrays.asList(SettingUserActivity.TIMEZONE_IDS).indexOf(selectedTimeZone);
    }

    // Múi giờ máy không nằm trong danh sách thì hiển thị nguyên ID
    private void showTimezone() {
        int index = selectedTimezoneIndex();
        tvTimezoneValue.setText(index >= 0 ? getResources().getStringArray(R.array.timezone_labels)[index] : selectedTimeZone);
    }

    private void showTimezonePicker() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.timezone_picker_title)
                .setSingleChoiceItems(R.array.timezone_labels, selectedTimezoneIndex(), (dialog, which) -> {
                    selectedTimeZone = SettingUserActivity.TIMEZONE_IDS[which];
                    showTimezone();
                    dialog.dismiss();
                })
                .show();
    }
}
