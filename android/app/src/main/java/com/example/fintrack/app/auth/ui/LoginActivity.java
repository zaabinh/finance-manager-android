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
import com.example.fintrack.app.user.remote.UserProfileResponse;
import com.example.fintrack.app.user.viewmodel.UserViewModel;
import com.example.fintrack.app.core.network.Resource;

/**
 * Màn hình đăng nhập người dùng.
 */
public class LoginActivity extends AppCompatActivity {

    // UI Elements
    private EditText etUsernameOrEmail;
    private EditText etPassword;
    private ImageView ivTogglePassword;
    private TextView tvForgotPassword;
    private Button btnLogin;
    private TextView tvSignUp;

    // States & Controllers
    private boolean isPasswordVisible = false;
    private AuthViewModel authViewModel;
    private UserViewModel userViewModel;
    private SessionManager sessionManager;
    private boolean onboardingCompletedAfterLogin;
    private boolean awaitingLoginProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Chuyển hướng ngay nếu đã đăng nhập trước đó
        sessionManager = SessionManager.getInstance(getApplicationContext());
        if (sessionManager.isLoggedIn() && !TextUtils.isEmpty(sessionManager.getUserId())) {
            // Chuyển sang đăng nhập nếu đã setup
            Class<?> nextActivity = sessionManager.isSetupCompleted()
                    ? HomeDashboardActivity.class
                    : com.example.fintrack.app.setup.ui.SetupWelcomeActivity.class;

            Intent intent = new Intent(this, nextActivity);
            startActivity(intent);
            finish();
            return;
        } else if (sessionManager.isLoggedIn()) {
            // Discard a partially saved session if profile loading did not finish after login.
            sessionManager.clearSession();
        }

        setContentView(R.layout.activity_auth_login);

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);

        initViews();
        setupListeners();
        setupObservers();
    }

    /**
     * Ánh xạ các View từ XML layout
     */
    private void initViews() {
        etUsernameOrEmail = findViewById(R.id.etUsernameOrEmail);
        etPassword = findViewById(R.id.etPassword);
        ivTogglePassword = findViewById(R.id.ivTogglePassword);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvSignUp = findViewById(R.id.tvSignUp);
    }

    /**
     * Thiết lập sự kiện click cho các nút và liên kết
     */
    private void setupListeners() {
        // Bật/tắt ẩn hiện mật khẩu
        ivTogglePassword.setOnClickListener(v -> isPasswordVisible = PasswordToggleHelper.togglePasswordVisibility(etPassword, ivTogglePassword, isPasswordVisible));

        // Thực hiện đăng nhập
        btnLogin.setOnClickListener(v -> handleLogin());

        // Chuyển tới màn hình Quên mật khẩu
        tvForgotPassword.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
            startActivity(intent);
        });

        // Chuyển tới màn hình Đăng ký
        tvSignUp.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }

    /**
     * Lắng nghe trạng thái xử lý đăng nhập từ ViewModel
     */
    private void setupObservers() {
        authViewModel.getLoginResult().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.getStatus()) {
                case SUCCESS:
                    btnLogin.setEnabled(true);
                    btnLogin.setText(R.string.btn_login);

                    // Đăng nhập thành công: Lưu phiên làm việc và chuyển màn hình
                    if (resource.getData() != null && resource.getData().getToken() != null
                            && !TextUtils.isEmpty(resource.getData().getToken().getAccessToken())) {
                        var login = resource.getData();
                        onboardingCompletedAfterLogin = login.isOnboardingCompleted();
                        awaitingLoginProfile = true;
                        sessionManager.saveAuthToken(
                                login.getToken().getAccessToken(),
                                login.getToken().getRefreshToken()
                        );
                        sessionManager.setSetupCompleted(onboardingCompletedAfterLogin);
                        userViewModel.fetchProfile();
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

        userViewModel.getProfileResult().observe(this, resource -> {
            if (!awaitingLoginProfile || resource == null) return;
            if (resource.getStatus() == Resource.Status.SUCCESS && resource.getData() != null) {
                UserProfileResponse profile = resource.getData();
                sessionManager.saveUserProfile(profile.getId(), profile.getEmail(), profile.getDisplayName());
                awaitingLoginProfile = false;
                Toast.makeText(this, R.string.login_success, Toast.LENGTH_SHORT).show();
                Class<?> nextActivity = onboardingCompletedAfterLogin
                        ? HomeDashboardActivity.class
                        : com.example.fintrack.app.setup.ui.SetupWelcomeActivity.class;
                Intent intent = new Intent(LoginActivity.this, nextActivity);
                startActivity(intent);
                finish();
            } else if (resource.getStatus() == Resource.Status.ERROR) {
                awaitingLoginProfile = false;
                sessionManager.clearSession();
                btnLogin.setEnabled(true);
                btnLogin.setText(R.string.btn_login);
                String message = resource.getMessage() != null
                        ? resource.getMessage() : getString(R.string.login_failed);
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Validate dữ liệu đầu vào và gửi yêu cầu đăng nhập
     */
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
