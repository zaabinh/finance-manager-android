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

import java.util.Objects;

public class SettingSecurityActivity extends AppCompatActivity {

    private EditText etCurrentPassword;
    private EditText etNewPassword;
    private EditText etConfirmPassword;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting_security);

        sessionManager = SessionManager.getInstance(getApplicationContext());

        initViews();
        setupListeners();
    }

    private void initViews() {
        ImageButton btnBack = findViewById(R.id.btnBack);
        etCurrentPassword = findViewById(R.id.etCurrentPassword);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        Button btnChangePassword = findViewById(R.id.btnChangePassword);

        TextView btnLogoutThisDevice = findViewById(R.id.btnLogoutThisDevice);
        TextView btnLogoutAllDevices = findViewById(R.id.btnLogoutAllDevices);
        TextView btnDeleteAccount = findViewById(R.id.btnDeleteAccount);

        btnBack.setOnClickListener(v -> finish());

        btnChangePassword.setOnClickListener(v -> {
            String currentPass = etCurrentPassword.getText().toString().trim();
            String newPass = etNewPassword.getText().toString().trim();
            String confirmPass = etConfirmPassword.getText().toString().trim();

            if (TextUtils.isEmpty(currentPass) || TextUtils.isEmpty(newPass) || TextUtils.isEmpty(confirmPass)) {
                Toast.makeText(this, R.string.error_password_empty, Toast.LENGTH_SHORT).show();
                return;
            }

            String passwordError = PasswordValidator.validatePassword(this, newPass);
            if (passwordError != null) {
                Toast.makeText(this, passwordError, Toast.LENGTH_SHORT).show();
                return;
            }

            if (!Objects.equals(newPass, confirmPass)) {
                Toast.makeText(this, R.string.error_password_mismatch, Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(this, R.string.msg_password_changed, Toast.LENGTH_SHORT).show();

            etCurrentPassword.setText("");
            etNewPassword.setText("");
            etConfirmPassword.setText("");
        });

        btnLogoutThisDevice.setOnClickListener(v -> {
            sessionManager.clearSession();
            Toast.makeText(this, R.string.msg_logged_out_device, Toast.LENGTH_SHORT).show();
            redirectToLogin();
        });

        btnLogoutAllDevices.setOnClickListener(v -> {
            sessionManager.clearSession();
            Toast.makeText(this, R.string.msg_logged_out_all, Toast.LENGTH_SHORT).show();
            redirectToLogin();
        });

        btnDeleteAccount.setOnClickListener(v -> {
            sessionManager.clearSession();
            Toast.makeText(this, R.string.msg_account_deleted, Toast.LENGTH_SHORT).show();
            redirectToLogin();
        });
    }

    private void setupListeners() {
    }

    private void redirectToLogin() {
        Intent intent = new Intent(SettingSecurityActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
