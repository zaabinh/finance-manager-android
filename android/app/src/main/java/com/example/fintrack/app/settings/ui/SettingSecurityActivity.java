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
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.R;
import com.example.fintrack.app.auth.ui.LoginActivity;
import com.example.fintrack.app.auth.util.PasswordValidator;
import com.example.fintrack.app.auth.viewmodel.AuthViewModel;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.SessionManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;

public class SettingSecurityActivity extends AppCompatActivity {

    private EditText etCurrentPassword;
    private EditText etNewPassword;
    private EditText etConfirmPassword;
    private Button btnChangePassword;
    private SessionManager sessionManager;
    private AuthViewModel authViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting_security);

        sessionManager = SessionManager.getInstance(getApplicationContext());
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

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
        authViewModel.getResetPasswordResult().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.getStatus()) {
                case SUCCESS:
                    btnChangePassword.setEnabled(true);
                    btnChangePassword.setText(R.string.btn_change_password);
                    Toast.makeText(this, R.string.msg_password_changed, Toast.LENGTH_SHORT).show();

                    etCurrentPassword.setText("");
                    etNewPassword.setText("");
                    etConfirmPassword.setText("");
                    break;

                case ERROR:
                    btnChangePassword.setEnabled(true);
                    btnChangePassword.setText(R.string.btn_change_password);
                    String errorMsg = resource.getMessage() != null ? resource.getMessage() : getString(R.string.error_server);
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    break;

                case LOADING:
                    btnChangePassword.setEnabled(false);
                    btnChangePassword.setText(getString(R.string.loading_processing));
                    break;
            }
        });
    }

    private void setupListeners() {
        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        TextView btnLogoutThisDevice = findViewById(R.id.btnLogoutThisDevice);
        TextView btnLogoutAllDevices = findViewById(R.id.btnLogoutAllDevices);
        TextView btnDeleteAccount = findViewById(R.id.btnDeleteAccount);

        btnChangePassword.setOnClickListener(v -> handleChangePassword());

        if (btnLogoutThisDevice != null) {
            btnLogoutThisDevice.setOnClickListener(v -> confirmAction(
                    getString(R.string.logout_this_device),
                    getString(R.string.msg_logged_out_device),
                    () -> {
                        sessionManager.clearSession();
                        redirectToLogin();
                    }
            ));
        }

        if (btnLogoutAllDevices != null) {
            btnLogoutAllDevices.setOnClickListener(v -> confirmAction(
                    getString(R.string.logout_all_devices),
                    getString(R.string.msg_logged_out_all),
                    () -> {
                        sessionManager.clearSession();
                        redirectToLogin();
                    }
            ));
        }

        if (btnDeleteAccount != null) {
            btnDeleteAccount.setOnClickListener(v -> confirmAction(
                    getString(R.string.delete_account),
                    getString(R.string.msg_account_deleted),
                    () -> {
                        sessionManager.clearSession();
                        redirectToLogin();
                    }
            ));
        }
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

        // Call ViewModel reset/change password API
        String email = sessionManager.getUserEmail();
        authViewModel.resetPassword(email != null ? email : "", "0000", newPass);
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
