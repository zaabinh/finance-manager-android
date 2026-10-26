package com.example.fintrack.app.auth.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.R;
import com.example.fintrack.app.auth.util.PasswordToggleHelper;
import com.example.fintrack.app.auth.util.PasswordValidator;
import com.example.fintrack.app.auth.viewmodel.AuthViewModel;

public class RegisterActivity extends AppCompatActivity {

    private EditText etUsername;
    private EditText etEmail;
    private EditText etPassword;
    private EditText etConfirmPassword;
    private ImageView ivTogglePassword;
    private ImageView ivToggleConfirmPassword;
    private CheckBox cbTerms;
    private Button btnRegister;
    private TextView tvSignIn;

    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;
    private AuthViewModel authViewModel;
    private String registeredEmail = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

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
    }

    private void setupListeners() {
        ivTogglePassword.setOnClickListener(v -> isPasswordVisible = PasswordToggleHelper.togglePasswordVisibility(etPassword, ivTogglePassword, isPasswordVisible));
        ivToggleConfirmPassword.setOnClickListener(v -> isConfirmPasswordVisible = PasswordToggleHelper.togglePasswordVisibility(etConfirmPassword, ivToggleConfirmPassword, isConfirmPasswordVisible));

        btnRegister.setOnClickListener(v -> handleRegister());

        tvSignIn.setOnClickListener(v -> finish());
    }

    private void setupObservers() {
        authViewModel.getRegisterResult().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.getStatus()) {
                case SUCCESS:
                    btnRegister.setEnabled(true);
                    btnRegister.setText(R.string.btn_register);
                    Toast.makeText(this, R.string.register_success, Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(RegisterActivity.this, VerifyEmailActivity.class);
                    intent.putExtra("email", registeredEmail);
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
}
