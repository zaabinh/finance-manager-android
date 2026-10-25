package com.example.fintrack.app.auth.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.R;
import com.example.fintrack.app.auth.viewmodel.AuthViewModel;

public class ChangePasswordActivity extends AppCompatActivity {

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

        initViews();
        setupListeners();
    }

    private void initViews() {
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmNewPassword = findViewById(R.id.etConfirmNewPassword);
        ivToggleNewPassword = findViewById(R.id.ivToggleNewPassword);
        ivToggleConfirmNewPassword = findViewById(R.id.ivToggleConfirmNewPassword);
        btnSubmit = findViewById(R.id.btnSubmit);
    }

    private void setupListeners() {
        ivToggleNewPassword.setOnClickListener(v -> toggleNewPasswordVisibility());
        ivToggleConfirmNewPassword.setOnClickListener(v -> toggleConfirmNewPasswordVisibility());

        btnSubmit.setOnClickListener(v -> handleSubmit());
    }

    private void toggleNewPasswordVisibility() {
        if (isNewPasswordVisible) {
            etNewPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
            ivToggleNewPassword.setAlpha(0.5f);
        } else {
            etNewPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            ivToggleNewPassword.setAlpha(1.0f);
        }
        isNewPasswordVisible = !isNewPasswordVisible;
        etNewPassword.setSelection(etNewPassword.getText().length());
    }

    private void toggleConfirmNewPasswordVisibility() {
        if (isConfirmNewPasswordVisible) {
            etConfirmNewPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
            ivToggleConfirmNewPassword.setAlpha(0.5f);
        } else {
            etConfirmNewPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            ivToggleConfirmNewPassword.setAlpha(1.0f);
        }
        isConfirmNewPasswordVisible = !isConfirmNewPasswordVisible;
        etConfirmNewPassword.setSelection(etConfirmNewPassword.getText().length());
    }

    private void handleSubmit() {
        String newPassword = etNewPassword.getText().toString().trim();
        String confirmNewPassword = etConfirmNewPassword.getText().toString().trim();

        if (TextUtils.isEmpty(newPassword)) {
            etNewPassword.setError(getString(R.string.error_required_field));
            etNewPassword.requestFocus();
            return;
        }

        if (newPassword.length() < 6) {
            etNewPassword.setError(getString(R.string.error_password_length));
            etNewPassword.requestFocus();
            return;
        }

        if (!newPassword.equals(confirmNewPassword)) {
            etConfirmNewPassword.setError(getString(R.string.error_password_not_match));
            etConfirmNewPassword.requestFocus();
            return;
        }

        btnSubmit.setEnabled(false);
        btnSubmit.setText(getString(R.string.loading_processing));

        authViewModel.resetPassword(email, newPassword).observe(this, resource -> {
            switch (resource.getStatus()) {
                case SUCCESS:
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText(R.string.btn_submit);
                    Toast.makeText(this, "Đổi mật khẩu thành công! Vui lòng đăng nhập lại.", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(ChangePasswordActivity.this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                    break;

                case ERROR:
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText(R.string.btn_submit);
                    String errorMsg = resource.getMessage() != null ? resource.getMessage() : "Đổi mật khẩu thất bại";
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    break;

                case LOADING:
                    break;
            }
        });
    }
}
