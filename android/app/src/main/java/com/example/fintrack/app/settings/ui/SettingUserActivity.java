package com.example.fintrack.app.settings.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fintrack.R;
import com.example.fintrack.app.core.session.SessionManager;

public class SettingUserActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private TextView btnEdit;
    private TextView tvDisplayName, tvDisplayEmail;
    private EditText etFullName, etEmail, etPhone;
    private Button btnSaveProfile, btnCancelEdit;

    private boolean isEditing = false;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting_user);

        sessionManager = SessionManager.getInstance(getApplicationContext());

        initViews();
        loadUserData();
        setupListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnEdit = findViewById(R.id.btnEdit);
        tvDisplayName = findViewById(R.id.tvDisplayName);
        tvDisplayEmail = findViewById(R.id.tvDisplayEmail);
        etFullName = findViewById(R.id.etFullName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnCancelEdit = findViewById(R.id.btnCancelEdit);
    }

    private void loadUserData() {
        String name = sessionManager.getUserName();
        String email = sessionManager.getUserEmail();

        if (name != null) {
            etFullName.setText(name);
            if (tvDisplayName != null) {
                tvDisplayName.setText(name);
            }
        }
        if (email != null) {
            etEmail.setText(email);
            if (tvDisplayEmail != null) {
                tvDisplayEmail.setText(email);
            }
        }
        etEmail.setEnabled(false); // Email thường không cho sửa trực tiếp
    }

    private void setupListeners() {
        // Nút quay lại màn hình trước
        btnBack.setOnClickListener(v -> finish());

        // Bấm nút "Chỉnh sửa" ở góc trên bên phải
        btnEdit.setOnClickListener(v -> toggleEditMode(true));

        // Nút hủy chỉnh sửa -> khôi phục dữ liệu ban đầu
        btnCancelEdit.setOnClickListener(v -> {
            loadUserData();
            toggleEditMode(false);
        });

        // Nút lưu thay đổi
        btnSaveProfile.setOnClickListener(v -> {
            String newName = etFullName.getText().toString().trim();
            if (newName.isEmpty()) {
                etFullName.setError("Tên không được để trống");
                return;
            }

            // Lưu tên mới vào SessionManager
            sessionManager.saveUserProfile(
                    sessionManager.getUserId(),
                    sessionManager.getUserEmail(),
                    newName
            );

            if (tvDisplayName != null) {
                tvDisplayName.setText(newName);
            }

            Toast.makeText(this, "Cập nhật thông tin thành công!", Toast.LENGTH_SHORT).show();
            toggleEditMode(false);
        });
    }

    // Bật/tắt trạng thái cho phép chỉnh sửa thông tin trên form
    private void toggleEditMode(boolean enable) {
        isEditing = enable;

        etFullName.setEnabled(enable);
        etPhone.setEnabled(enable);

        if (enable) {
            btnSaveProfile.setVisibility(View.VISIBLE);
            btnCancelEdit.setVisibility(View.VISIBLE);
            btnEdit.setVisibility(View.GONE);
        } else {
            btnSaveProfile.setVisibility(View.GONE);
            btnCancelEdit.setVisibility(View.GONE);
            btnEdit.setVisibility(View.VISIBLE);
        }
    }
}
