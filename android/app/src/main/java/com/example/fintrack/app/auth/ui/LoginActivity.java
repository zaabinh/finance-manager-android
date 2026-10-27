package com.example.fintrack.app.auth.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.R;
import com.example.fintrack.app.auth.util.PasswordToggleHelper;
import com.example.fintrack.app.auth.viewmodel.AuthViewModel;
import com.example.fintrack.app.core.session.SessionManager;
import com.example.fintrack.app.home.ui.HomeDashboardActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText etUsernameOrEmail;
    private EditText etPassword;
    private ImageView ivTogglePassword;
    private TextView tvForgotPassword;
    private Button btnLogin;
    private TextView tvSignUp;

    private boolean isPasswordVisible = false;
    private AuthViewModel authViewModel;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = SessionManager.getInstance(getApplicationContext());
        if (sessionManager.isLoggedIn()) {
            Intent intent = new Intent(this, HomeDashboardActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        initViews();
        setupListeners();
        setupObservers();
    }

    private void initViews() {
        etUsernameOrEmail = findViewById(R.id.etUsernameOrEmail);
        etPassword = findViewById(R.id.etPassword);
        ivTogglePassword = findViewById(R.id.ivTogglePassword);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvSignUp = findViewById(R.id.tvSignUp);
    }

    private void setupListeners() {
        ivTogglePassword.setOnClickListener(v -> isPasswordVisible = PasswordToggleHelper.togglePasswordVisibility(etPassword, ivTogglePassword, isPasswordVisible));
        btnLogin.setOnClickListener(v -> handleLogin());

        tvForgotPassword.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
            startActivity(intent);
        });

        tvSignUp.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }

    private void setupObservers() {
        authViewModel.getLoginResult().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.getStatus()) {
                case SUCCESS:
                    btnLogin.setEnabled(true);
                    btnLogin.setText(R.string.btn_login);
                    if (resource.getData() != null && !TextUtils.isEmpty(resource.getData().getAccessToken())) {
                        sessionManager.saveAuthToken(
                                resource.getData().getAccessToken(),
                                resource.getData().getRefreshToken()
                        );
                        sessionManager.saveUserProfile(
                                resource.getData().getUserId(),
                                resource.getData().getEmail(),
                                resource.getData().getName()
                        );
                        Toast.makeText(this, R.string.login_success, Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(LoginActivity.this, HomeDashboardActivity.class);
                        startActivity(intent);
                        finish();
                    } else {
                        Toast.makeText(this, R.string.login_response_invalid, Toast.LENGTH_LONG).show();
                    }
                    break;

                case ERROR:
                    btnLogin.setEnabled(true);
                    btnLogin.setText(R.string.btn_login);
                    String errorMsg = resource.getMessage() != null ? resource.getMessage() : getString(R.string.login_failed);
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    break;

                case LOADING:
                    btnLogin.setEnabled(false);
                    btnLogin.setText(getString(R.string.loading_login));
                    break;
            }
        });
    }

    private void handleLogin() {
        String usernameOrEmail = etUsernameOrEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        if (TextUtils.isEmpty(usernameOrEmail)) {
            etUsernameOrEmail.setError(getString(R.string.error_required_field));
            etUsernameOrEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            etPassword.setError(getString(R.string.error_required_field));
            etPassword.requestFocus();
            return;
        }

        authViewModel.login(usernameOrEmail, password);
    }
}
