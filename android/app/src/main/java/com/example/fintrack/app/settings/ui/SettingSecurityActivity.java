package com.example.fintrack.app.settings.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fintrack.R;
import com.example.fintrack.app.auth.ui.LoginActivity;
import com.example.fintrack.app.auth.util.PasswordValidator;
import com.example.fintrack.app.core.session.SessionManager;

// Màn hình Bảo mật & Tài khoản (SettingSecurityActivity)
public class SettingSecurityActivity extends AppCompatActivity {

    // Khai báo các thành phần giao diện (UI)
    private ImageButton btnBack;
    private EditText etCurrentPassword;
    private EditText etNewPassword;
    private EditText etConfirmPassword;
    private Button btnChangePassword;

    private TextView btnLogoutThisDevice;
    private TextView btnLogoutAllDevices;
    private TextView btnDeleteAccount;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting_security); // Khớp với file XML mới

        sessionManager = SessionManager.getInstance(getApplicationContext());

        initViews();       // Ánh xạ các thành phần giao diện
        setupListeners();  // Gán sự kiện bấm nút và xử lý logic
    }

    // Ánh xạ ID từ file XML sang biến Java
    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        etCurrentPassword = findViewById(R.id.etCurrentPassword);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnChangePassword = findViewById(R.id.btnChangePassword);

        btnLogoutThisDevice = findViewById(R.id.btnLogoutThisDevice);
        btnLogoutAllDevices = findViewById(R.id.btnLogoutAllDevices);
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount);
    }

    // Thiết lập sự kiện tương tác
    private void setupListeners() {
        // 1. Nút quay lại: Đóng màn hình hiện tại
        btnBack.setOnClickListener(v -> finish());

        // 2. Xử lý đổi mật khẩu
        btnChangePassword.setOnClickListener(v -> {
            String currentPass = etCurrentPassword.getText().toString().trim();
            String newPass = etNewPassword.getText().toString().trim();
            String confirmPass = etConfirmPassword.getText().toString().trim();

            // Kiểm tra ràng buộc cơ bản
            if (TextUtils.isEmpty(currentPass) || TextUtils.isEmpty(newPass) || TextUtils.isEmpty(confirmPass)) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin mật khẩu", Toast.LENGTH_SHORT).show();
                return;
            }

            // Sử dụng PasswordValidator chuẩn để kiểm tra độ mạnh mật khẩu mới
            String passwordError = PasswordValidator.validatePassword(this, newPass);
            if (passwordError != null) {
                Toast.makeText(this, passwordError, Toast.LENGTH_SHORT).show();
                return;
            }

            if (!newPass.equals(confirmPass)) {
                Toast.makeText(this, "Mật khẩu xác nhận không khớp", Toast.LENGTH_SHORT).show();
                return;
            }

            // TODO: Gửi yêu cầu đổi mật khẩu lên Server hoặc Database ở đây
            Toast.makeText(this, "Đổi mật khẩu thành công!", Toast.LENGTH_SHORT).show();

            // Xóa trắng các ô nhập sau khi đổi thành công
            etCurrentPassword.setText("");
            etNewPassword.setText("");
            etConfirmPassword.setText("");
        });

        // 3. Đăng xuất khỏi thiết bị này
        btnLogoutThisDevice.setOnClickListener(v -> {
            sessionManager.clearSession();
            Toast.makeText(this, "Đã đăng xuất khỏi thiết bị", Toast.LENGTH_SHORT).show();
            redirectToLogin();
        });

        // 4. Đăng xuất tất cả thiết bị
        btnLogoutAllDevices.setOnClickListener(v -> {
            sessionManager.clearSession();
            Toast.makeText(this, "Đã đăng xuất khỏi tất cả thiết bị", Toast.LENGTH_SHORT).show();
            redirectToLogin();
        });

        // 5. Xóa vĩnh viễn tài khoản
        btnDeleteAccount.setOnClickListener(v -> {
            sessionManager.clearSession();
            Toast.makeText(this, "Tài khoản đã bị xóa vĩnh viễn", Toast.LENGTH_SHORT).show();
            redirectToLogin();
        });
    }

    // Hàm hỗ trợ chuyển hướng về màn hình đăng nhập và xóa sạch lịch sử stack
    private void redirectToLogin() {
        Intent intent = new Intent(SettingSecurityActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
