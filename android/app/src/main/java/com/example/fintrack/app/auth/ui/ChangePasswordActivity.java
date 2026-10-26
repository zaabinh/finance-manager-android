package com.example.fintrack.app.auth.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.R;
import com.example.fintrack.app.auth.util.PasswordToggleHelper;
import com.example.fintrack.app.auth.util.PasswordValidator;
import com.example.fintrack.app.auth.viewmodel.AuthViewModel;

public class ChangePasswordActivity extends AppCompatActivity {

    private EditText etResetOtp;
    private EditText etNewPassword;
    private EditText etConfirmNewPassword;
    private ImageView ivToggleNewPassword;
    private ImageView ivToggleConfirmNewPassword;
    private Button btnSubmit;

    private boolean isNewPasswordVisible = false;
    private boolean isConfirmNewPasswordVisible = false;

    private AuthViewModel authViewModel;
    private String email;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        email = getIntent().getStringExtra("email");
        if (email == null) {
            email = "";
        }

        initViews();
        setupListeners();
        setupObservers();
    }

    private void initViews() {
        etResetOtp = findViewById(R.id.etResetOtp);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmNewPassword = findViewById(R.id.etConfirmNewPassword);
        ivToggleNewPassword = findViewById(R.id.ivToggleNewPassword);
        ivToggleConfirmNewPassword = findViewById(R.id.ivToggleConfirmNewPassword);
        btnSubmit = findViewById(R.id.btnSubmit);
    }

    private void setupListeners() {
        ivToggleNewPassword.setOnClickListener(v -> isNewPasswordVisible = PasswordToggleHelper.togglePasswordVisibility(etNewPassword, ivToggleNewPassword, isNewPasswordVisible));
        ivToggleConfirmNewPassword.setOnClickListener(v -> isConfirmNewPasswordVisible = PasswordToggleHelper.togglePasswordVisibility(etConfirmNewPassword, ivToggleConfirmNewPassword, isConfirmNewPasswordVisible));

        btnSubmit.setOnClickListener(v -> handleSubmit());
    }

    private void setupObservers() {
        authViewModel.getResetPasswordResult().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.getStatus()) {
                case SUCCESS:
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText(R.string.btn_submit);
                    Toast.makeText(this, R.string.reset_password_success, Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(ChangePasswordActivity.this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                    break;

                case ERROR:
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText(R.string.btn_submit);
                    String errorMsg = resource.getMessage() != null ? resource.getMessage() : getString(R.string.reset_password_failed);
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    break;

                case LOADING:
                    btnSubmit.setEnabled(false);
                    btnSubmit.setText(getString(R.string.loading_processing));
                    break;
            }
        });
    }

    private void handleSubmit() {
        String otp = etResetOtp.getText().toString().trim();
        String newPassword = etNewPassword.getText().toString();
        String confirmNewPassword = etConfirmNewPassword.getText().toString();

        if (TextUtils.isEmpty(otp)) {
            etResetOtp.setError(getString(R.string.error_required_field));
            etResetOtp.requestFocus();
            return;
        }

        String passwordError = PasswordValidator.validatePassword(this, newPassword);
        if (passwordError != null) {
            etNewPassword.setError(passwordError);
            etNewPassword.requestFocus();
            return;
        }

        if (!newPassword.equals(confirmNewPassword)) {
            etConfirmNewPassword.setError(getString(R.string.error_password_not_match));
            etConfirmNewPassword.requestFocus();
            return;
        }

        authViewModel.resetPassword(email, otp, newPassword);
    }
}
