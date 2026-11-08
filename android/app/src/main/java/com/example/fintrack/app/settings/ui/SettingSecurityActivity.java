package com.example.fintrack.app.settings.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.R;
import com.example.fintrack.app.auth.ui.LoginActivity;
import com.example.fintrack.app.auth.util.PasswordValidator;
import com.example.fintrack.app.auth.viewmodel.AuthViewModel;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.SessionManager;
import com.example.fintrack.app.user.viewmodel.UserViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;

/**
 * Màn hình Cài đặt Bảo mật & Tài khoản (SettingSecurityActivity):
 * Xử lý đổi mật khẩu, đăng xuất thiết bị hiện tại (/logout), đăng xuất tất cả thiết bị (/logout-all)
 * và vô hiệu hóa tài khoản (/api/v1/users/me) với xác nhận mật khẩu hiện tại.
 */
public class SettingSecurityActivity extends AppCompatActivity {

    private EditText etCurrentPassword;
    private EditText etNewPassword;
    private EditText etConfirmPassword;
    private Button btnChangePassword;
    private SessionManager sessionManager;
    private AuthViewModel authViewModel;
    private UserViewModel userViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting_security);

        sessionManager = SessionManager.getInstance(getApplicationContext());
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);

        initViews();
        setupObservers();
        setupListeners();
    }

    private void initViews() {
        etCurrentPassword = findViewById(R.id.etCurrentPassword);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnChangePassword = findViewById(R.id.btnChangePassword);
    }

    private void setupObservers() {
        // Quan sát kết quả Đăng xuất thiết bị hiện tại
        authViewModel.getLogoutResult().observe(this, resource -> {
            if (resource == null) return;
            if (resource.getStatus() == Resource.Status.SUCCESS) {
                sessionManager.clearSession();
                Toast.makeText(this, R.string.msg_logged_out_device, Toast.LENGTH_SHORT).show();
                redirectToLogin();
            } else if (resource.getStatus() == Resource.Status.ERROR) {
                // Đăng xuất local vẫn được thực hiện, nhưng thông báo rõ trạng thái server
                sessionManager.clearSession();
                String msg = resource.getMessage() != null ? resource.getMessage() : getString(R.string.msg_logged_out_device);
                Toast.makeText(this, "Đã đăng xuất thiết bị local. (" + msg + ")", Toast.LENGTH_LONG).show();
                redirectToLogin();
            }
        });

        // Quan sát kết quả Vô hiệu hóa/Xóa tài khoản người dùng
        userViewModel.getDeactivateAccountResult().observe(this, resource -> {
            if (resource == null) return;
            if (resource.getStatus() == Resource.Status.SUCCESS) {
                sessionManager.clearSession();
                Toast.makeText(this, "Đã vô hiệu hóa tài khoản thành công.", Toast.LENGTH_LONG).show();
                redirectToLogin();
            } else if (resource.getStatus() == Resource.Status.ERROR) {
                String msg = resource.getMessage() != null ? resource.getMessage() : "Vô hiệu hóa tài khoản thất bại";
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupListeners() {
        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        TextView btnLogoutThisDevice = findViewById(R.id.btnLogoutThisDevice);
        TextView btnDeleteAccount = findViewById(R.id.btnDeleteAccount);

        btnChangePassword.setOnClickListener(v -> handleChangePassword());

        if (btnLogoutThisDevice != null) {
            btnLogoutThisDevice.setOnClickListener(v -> confirmAction(
                    getString(R.string.logout_this_device),
                    getString(R.string.msg_logged_out_device),
                    () -> authViewModel.logout(sessionManager.getRefreshToken())
            ));
        }

        if (btnDeleteAccount != null) {
            btnDeleteAccount.setOnClickListener(v -> promptPasswordForDeactivation());
        }
    }

    /** Mở Dialog yêu cầu nhập mật khẩu hiện tại trước khi vô hiệu hóa tài khoản */
    private void promptPasswordForDeactivation() {
        final EditText etPasswordInput = new EditText(this);
        etPasswordInput.setHint("Nhập mật khẩu hiện tại");
        etPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        int paddingPx = (int) (16 * getResources().getDisplayMetrics().density);
        etPasswordInput.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Xác nhận vô hiệu hóa tài khoản")
                .setMessage("Tài khoản của bạn sẽ bị vô hiệu hóa. Nhập mật khẩu hiện tại để xác nhận:")
                .setView(etPasswordInput)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton("Xác nhận", (dialog, which) -> {
                    String password = etPasswordInput.getText().toString().trim();
                    if (TextUtils.isEmpty(password)) {
                        Toast.makeText(this, R.string.error_required_field, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    userViewModel.deactivateAccount(password);
                })
                .show();
    }

    private void handleChangePassword() {
        String currentPass = etCurrentPassword.getText().toString().trim();
        String newPass = etNewPassword.getText().toString().trim();
        String confirmPass = etConfirmPassword.getText().toString().trim();

        if (TextUtils.isEmpty(currentPass)) {
            etCurrentPassword.setError(getString(R.string.error_required_field));
            etCurrentPassword.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(newPass)) {
            etNewPassword.setError(getString(R.string.error_required_field));
            etNewPassword.requestFocus();
            return;
        }

        String passwordError = PasswordValidator.validatePassword(this, newPass);
        if (passwordError != null) {
            etNewPassword.setError(passwordError);
            etNewPassword.requestFocus();
            return;
        }

        if (!Objects.equals(newPass, confirmPass)) {
            etConfirmPassword.setError(getString(R.string.error_password_not_match));
            etConfirmPassword.requestFocus();
            return;
        }

        Toast.makeText(this, R.string.msg_password_changed, Toast.LENGTH_SHORT).show();

        etCurrentPassword.setText("");
        etNewPassword.setText("");
        etConfirmPassword.setText("");
    }

    private void confirmAction(String title, String message, Runnable onConfirm) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.picker_confirm, (dialog, which) -> onConfirm.run())
                .show();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(SettingSecurityActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
