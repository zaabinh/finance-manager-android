package com.example.fintrack.app.auth.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.TextViewCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.R;
import com.example.fintrack.app.auth.util.PasswordToggleHelper;
import com.example.fintrack.app.auth.util.PasswordValidator;
import com.example.fintrack.app.auth.viewmodel.AuthViewModel;
import com.google.android.material.progressindicator.LinearProgressIndicator;

/**
 * Màn hình Đăng ký tài khoản (RegisterActivity).
 * Chịu trách nhiệm hiển thị giao diện nhập thông tin đăng ký, validate dữ liệu,
 * hiển thị trực quan thanh đo độ mạnh mật khẩu và danh sách quy tắc tick xanh,
 * gọi ViewModel để gửi request lên server và điều hướng sang màn hình xác thực OTP nếu thành công.
 */
public class RegisterActivity extends AppCompatActivity {

    private static final String KEY_REGISTERED_EMAIL = "key_registered_email";

    // Các thành phần giao diện (UI Views)
    private EditText etUsername;
    private EditText etEmail;
    private EditText etPassword;
    private EditText etConfirmPassword;
    private ImageView ivTogglePassword;
    private ImageView ivToggleConfirmPassword;
    private CheckBox cbTerms;
    private Button btnRegister;
    private TextView tvSignIn;

    // Các view kiểm tra độ mạnh mật khẩu
    private TextView tvPasswordStrengthLabel;
    private LinearProgressIndicator pbPasswordStrength;
    private TextView ruleMinLength;
    private TextView ruleLowercase;
    private TextView ruleUppercase;
    private TextView ruleDigitOrSpecial;

    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;
    private AuthViewModel authViewModel;
    private String registeredEmail = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth_register);

        if (savedInstanceState != null) {
            registeredEmail = savedInstanceState.getString(KEY_REGISTERED_EMAIL, "");
        }

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        initViews();
        setupListeners();
        setupObservers();
    }

    private void initViews() {
        etUsername = findViewById(R.id.etUsername);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        ivTogglePassword = findViewById(R.id.ivTogglePassword);
        ivToggleConfirmPassword = findViewById(R.id.ivToggleConfirmPassword);
        cbTerms = findViewById(R.id.cbTerms);
        btnRegister = findViewById(R.id.btnRegister);
        tvSignIn = findViewById(R.id.tvSignIn);

        tvPasswordStrengthLabel = findViewById(R.id.tvPasswordStrengthLabel);
        pbPasswordStrength = findViewById(R.id.pbPasswordStrength);
        ruleMinLength = findViewById(R.id.ruleMinLength);
        ruleLowercase = findViewById(R.id.ruleLowercase);
        ruleUppercase = findViewById(R.id.ruleUppercase);
        ruleDigitOrSpecial = findViewById(R.id.ruleDigitOrSpecial);
    }

    private void setupListeners() {
        ivTogglePassword.setOnClickListener(v -> isPasswordVisible = PasswordToggleHelper.togglePasswordVisibility(etPassword, ivTogglePassword, isPasswordVisible));
        ivToggleConfirmPassword.setOnClickListener(v -> isConfirmPasswordVisible = PasswordToggleHelper.togglePasswordVisibility(etConfirmPassword, ivToggleConfirmPassword, isConfirmPasswordVisible));

        // Theo dõi trực quan độ mạnh mật khẩu và các quy tắc khi gõ
        etPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String pwd = s != null ? s.toString() : "";
                int percent = PasswordValidator.calculateStrengthPercentage(pwd);

                if (pbPasswordStrength != null) {
                    pbPasswordStrength.setProgress(percent);
                    if (percent < 50) {
                        pbPasswordStrength.setIndicatorColor(getColor(R.color.status_error));
                    } else if (percent < 100) {
                        pbPasswordStrength.setIndicatorColor(getColor(R.color.status_warning));
                    } else {
                        pbPasswordStrength.setIndicatorColor(getColor(R.color.status_success));
                    }
                }

                if (tvPasswordStrengthLabel != null) {
                    tvPasswordStrengthLabel.setText(getString(R.string.password_strength_format, percent));
                }

                updateRuleUI(ruleMinLength, PasswordValidator.hasMinLength(pwd));
                updateRuleUI(ruleLowercase, PasswordValidator.hasLowercase(pwd));
                updateRuleUI(ruleUppercase, PasswordValidator.hasUppercase(pwd));
                updateRuleUI(ruleDigitOrSpecial, PasswordValidator.hasDigitOrSpecial(pwd));
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnRegister.setOnClickListener(v -> handleRegister());
        tvSignIn.setOnClickListener(v -> finish());
    }

    private void updateRuleUI(TextView ruleView, boolean met) {
        if (ruleView == null) return;
        if (met) {
            ruleView.setTextColor(getColor(R.color.status_success));
            TextViewCompat.setCompoundDrawableTintList(ruleView,
                    ColorStateList.valueOf(getColor(R.color.status_success)));
        } else {
            ruleView.setTextColor(getColor(R.color.text_secondary));
            TextViewCompat.setCompoundDrawableTintList(ruleView,
                    ColorStateList.valueOf(getColor(R.color.text_disabled)));
        }
    }

    private void setupObservers() {
        authViewModel.getRegisterResult().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.getStatus()) {
                case SUCCESS:
                    btnRegister.setEnabled(true);
                    btnRegister.setText(R.string.btn_register);
                    Toast.makeText(this, R.string.register_success, Toast.LENGTH_SHORT).show();

                    String emailToPass = authViewModel.getRegisteredEmail();
                    if (TextUtils.isEmpty(emailToPass)) {
                        emailToPass = registeredEmail;
                    }

                    Intent intent = new Intent(RegisterActivity.this, VerifyEmailActivity.class);
                    intent.putExtra("email", emailToPass);
                    startActivity(intent);
                    finish();
                    break;

                case ERROR:
                    btnRegister.setEnabled(true);
                    btnRegister.setText(R.string.btn_register);
                    String errorMsg = resource.getMessage() != null ? resource.getMessage() : getString(R.string.register_failed);
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    break;

                case LOADING:
                    btnRegister.setEnabled(false);
                    btnRegister.setText(getString(R.string.loading_register));
                    break;
            }
        });
    }

    private void handleRegister() {
        String username = etUsername.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();

        if (TextUtils.isEmpty(username)) {
            etUsername.setError(getString(R.string.error_required_field));
            etUsername.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email)) {
            etEmail.setError(getString(R.string.error_required_field));
            etEmail.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError(getString(R.string.error_invalid_email));
            etEmail.requestFocus();
            return;
        }

        String passwordError = PasswordValidator.validatePassword(this, password);
        if (passwordError != null) {
            etPassword.setError(passwordError);
            etPassword.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError(getString(R.string.error_password_not_match));
            etConfirmPassword.requestFocus();
            return;
        }

        if (!cbTerms.isChecked()) {
            Toast.makeText(this, R.string.error_agree_terms, Toast.LENGTH_SHORT).show();
            return;
        }

        registeredEmail = email;
        authViewModel.register(username, email, password);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_REGISTERED_EMAIL, registeredEmail);
    }
}
