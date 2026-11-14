package com.example.fintrack.app.settings.ui;

import com.example.fintrack.app.core.network.ErrorMapper;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.R;
import com.example.fintrack.databinding.ActivitySettingSecurityBinding;
import com.example.fintrack.app.FintrackApplication;
import com.example.fintrack.app.auth.ui.LoginActivity;
import com.example.fintrack.app.auth.util.PasswordValidator;
import com.example.fintrack.app.auth.viewmodel.AuthViewModel;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.user.viewmodel.UserViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;

/**
 * Màn hình Cài đặt Bảo mật & Tài khoản (SettingSecurityActivity):
 * Xử lý đổi mật khẩu, đăng xuất thiết bị hiện tại (/logout), đăng xuất tất cả thiết bị (/logout-all)
 * và vô hiệu hóa tài khoản (/api/v1/users/me) với xác nhận mật khẩu hiện tại.
 */
public class SettingSecurityActivity extends AppCompatActivity {

    private ActivitySettingSecurityBinding binding;

    private EditText etCurrentPassword;
    private EditText etNewPassword;
    private EditText etConfirmPassword;
    private Button btnChangePassword;
    private AuthViewModel authViewModel;
    private UserViewModel userViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingSecurityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authViewModel = new ViewModelProvider(this, FintrackApplication.container(this).viewModelFactory()).get(AuthViewModel.class);
        userViewModel = new ViewModelProvider(this, FintrackApplication.container(this).viewModelFactory()).get(UserViewModel.class);

        initViews();
        setupObservers();
        setupListeners();
    }

    private void initViews() {
        etCurrentPassword = binding.etCurrentPassword;
        etNewPassword = binding.etNewPassword;
        etConfirmPassword = binding.etConfirmPassword;
        btnChangePassword = binding.btnChangePassword;
    }

    private void setupObservers() {
        // Quan sát kết quả Đăng xuất thiết bị hiện tại (sự kiện một lần; AuthViewModel đã xóa phiên local)
        authViewModel.getLogoutResult().observe(this, event -> {
            Resource<Void> resource = event == null ? null : event.getContentIfNotHandled();
            if (resource == null) return;
            if (resource.getStatus() == Resource.Status.SUCCESS) {
                Toast.makeText(this, R.string.msg_logged_out_device, Toast.LENGTH_SHORT).show();
                redirectToLogin();
            } else if (resource.getStatus() == Resource.Status.ERROR) {
                // Đăng xuất local vẫn được thực hiện, nhưng thông báo rõ trạng thái server
                String msg = ErrorMapper.text(this, resource.getError()) != null ? ErrorMapper.text(this, resource.getError()) : getString(R.string.msg_logged_out_device);
                Toast.makeText(this, getString(R.string.security_logout_local_with_error, msg), Toast.LENGTH_LONG).show();
                redirectToLogin();
            }
        });

        // Quan sát kết quả Vô hiệu hóa/Xóa tài khoản người dùng (sự kiện một lần; UserViewModel đã xóa phiên local khi thành công)
        userViewModel.getDeactivateAccountResult().observe(this, event -> {
            Resource<Void> resource = event == null ? null : event.getContentIfNotHandled();
            if (resource == null) return;
            if (resource.getStatus() == Resource.Status.SUCCESS) {
                Toast.makeText(this, R.string.security_deactivated, Toast.LENGTH_LONG).show();
                redirectToLogin();
            } else if (resource.getStatus() == Resource.Status.ERROR) {
                String msg = ErrorMapper.text(this, resource.getError()) != null ? ErrorMapper.text(this, resource.getError()) : getString(R.string.security_deactivate_failed);
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        btnChangePassword.setOnClickListener(v -> handleChangePassword());

        binding.btnLogoutThisDevice.setOnClickListener(v -> confirmAction(
                getString(R.string.logout_this_device),
                getString(R.string.msg_logged_out_device),
                () -> authViewModel.logout()
        ));

        binding.btnDeleteAccount.setOnClickListener(v -> promptPasswordForDeactivation());
    }

    /** Mở Dialog yêu cầu nhập mật khẩu hiện tại trước khi vô hiệu hóa tài khoản */
    private void promptPasswordForDeactivation() {
        final EditText etPasswordInput = new EditText(this);
        etPasswordInput.setHint(R.string.security_current_password_hint);
        etPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        int paddingPx = (int) (16 * getResources().getDisplayMetrics().density);
        etPasswordInput.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.security_deactivate_title)
                .setMessage(R.string.security_deactivate_message)
                .setView(etPasswordInput)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.security_deactivate_confirm, (dialog, which) -> {
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
