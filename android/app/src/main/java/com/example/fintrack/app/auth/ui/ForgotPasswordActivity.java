package com.example.fintrack.app.auth.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.R;
import com.example.fintrack.app.auth.viewmodel.AuthViewModel;

public class ForgotPasswordActivity extends AppCompatActivity {

    private static final String KEY_REQUESTED_EMAIL = "key_requested_email";

    private EditText etEmail;
    private Button btnSend;
    private TextView tvBackToLogin;
    private AuthViewModel authViewModel;
    private String requestedEmail = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        if (savedInstanceState != null) {
            requestedEmail = savedInstanceState.getString(KEY_REQUESTED_EMAIL, "");
        }

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        initViews();
        setupListeners();
        setupObservers();
    }

    private void initViews() {
        etEmail = findViewById(R.id.etEmail);
        btnSend = findViewById(R.id.btnSend);
        tvBackToLogin = findViewById(R.id.tvBackToLogin);
    }

    private void setupListeners() {
        btnSend.setOnClickListener(v -> handleSend());
        tvBackToLogin.setOnClickListener(v -> finish());
    }

    private void setupObservers() {
        authViewModel.getForgotPasswordResult().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.getStatus()) {
                case SUCCESS:
                    btnSend.setEnabled(true);
                    btnSend.setText(R.string.btn_send);
                    Toast.makeText(this, R.string.forgot_password_success, Toast.LENGTH_SHORT).show();

                    String emailToPass = authViewModel.getForgotPasswordEmail();
                    if (TextUtils.isEmpty(emailToPass)) {
                        emailToPass = requestedEmail;
                    }

                    Intent intent = new Intent(ForgotPasswordActivity.this, ChangePasswordActivity.class);
                    intent.putExtra("email", emailToPass);
                    startActivity(intent);
                    finish();
                    break;

                case ERROR:
                    btnSend.setEnabled(true);
                    btnSend.setText(R.string.btn_send);
                    String errorMsg = resource.getMessage() != null ? resource.getMessage() : getString(R.string.forgot_password_failed);
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    break;

                case LOADING:
                    btnSend.setEnabled(false);
                    btnSend.setText(getString(R.string.loading_send_code));
                    break;
            }
        });
    }

    private void handleSend() {
        String email = etEmail.getText().toString().trim();

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

        requestedEmail = email;
        authViewModel.forgotPassword(email);
    }

    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_REQUESTED_EMAIL, requestedEmail);
    }
}
