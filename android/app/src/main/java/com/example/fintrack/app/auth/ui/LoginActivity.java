package com.example.fintrack.app.auth.ui;

import com.example.fintrack.app.core.network.ErrorMapper;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.R;
import com.example.fintrack.app.FintrackApplication;
import com.example.fintrack.app.auth.util.PasswordToggleHelper;
import com.example.fintrack.app.auth.viewmodel.AuthViewModel;
import com.example.fintrack.app.core.session.SessionSnapshot;
import com.example.fintrack.app.home.ui.HomeDashboardActivity;

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
    private static final String KEY_PENDING_VERIFICATION_EMAIL = "key_pending_verification_email";
    // Email the last resend-verification request was sent for; survives rotation while the request runs.
    private String pendingVerificationEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        // Chuyển hướng ngay nếu đã đăng nhập trước đó
        SessionSnapshot session = FintrackApplication.container(this).sessionStore().current();
        if (session.loggedIn && !TextUtils.isEmpty(session.userId)) {
            // Chuyển sang đăng nhập nếu đã setup
            startActivity(new Intent(this, destination(session.setupCompleted)));
            finish();
            return;
        }

        setContentView(R.layout.activity_auth_login);
        if (savedInstanceState != null)
            pendingVerificationEmail = savedInstanceState.getString(KEY_PENDING_VERIFICATION_EMAIL);

        authViewModel = new ViewModelProvider(this, FintrackApplication.container(this).viewModelFactory()).get(AuthViewModel.class);
        // Discard a partially saved session if profile loading did not finish after login. A login that is
        // still running in the retained ViewModel (Activity recreated) is left alone.
        authViewModel.discardIncompleteSession();

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

                    // Đăng nhập thành công: AuthViewModel lưu phiên làm việc, tải hồ sơ rồi phát sự kiện chuyển màn hình
                    if (resource.getData() == null || resource.getData().getToken() == null
                            || TextUtils.isEmpty(resource.getData().getToken().getAccessToken())) {
                        Toast.makeText(this, R.string.login_response_invalid, Toast.LENGTH_LONG).show();
                    }
                    break;

                case ERROR:
                    btnLogin.setEnabled(true);
                    btnLogin.setText(R.string.btn_login);
                    String errorMsg = ErrorMapper.text(this, resource.getError()) != null ? ErrorMapper.text(this, resource.getError()) : getString(R.string.login_failed);

                    if ("AUTH_EMAIL_NOT_VERIFIED".equals(resource.getError().code)) {
                        promptResendVerification(etUsernameOrEmail.getText().toString().trim());
                    } else {
                        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    }
                    break;

                case LOADING:
                    btnLogin.setEnabled(false);
                    btnLogin.setText(getString(R.string.loading_login));
                    break;
            }
        });

        // Sự kiện một lần: phiên và hồ sơ đã được lưu. Lỗi tải hồ sơ đi qua getLoginResult() ở trên.
        authViewModel.getLoginNavigation().observe(this, event -> {
            AuthViewModel.LoginDestination destination = event == null ? null : event.getContentIfNotHandled();
            if (destination == null) return;
            Toast.makeText(this, R.string.login_success, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(LoginActivity.this, destination(destination == AuthViewModel.LoginDestination.HOME)));
            finish();
        });

        authViewModel.getResendVerificationResult().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.getStatus()) {
                case SUCCESS:
                    // Null after navigating: a LiveData replay on recreation must not reopen the OTP screen.
                    if (pendingVerificationEmail == null) break;
                    Toast.makeText(this, R.string.verify_resend_sent, Toast.LENGTH_LONG).show();
                    authViewModel.setRegisteredEmail(pendingVerificationEmail);
                    Intent intent = new Intent(LoginActivity.this, VerifyEmailActivity.class);
                    intent.putExtra("email", pendingVerificationEmail);
                    pendingVerificationEmail = null;
                    startActivity(intent);
                    break;
                case ERROR:
                    if (pendingVerificationEmail == null) break;
                    pendingVerificationEmail = null;
                    String msg = ErrorMapper.text(this, resource.getError()) != null ? ErrorMapper.text(this, resource.getError()) : getString(R.string.verify_resend_failed);
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    break;
                case LOADING:
                    Toast.makeText(this, R.string.verify_resend_sending, Toast.LENGTH_SHORT).show();
                    break;
            }
        });
    }

    private static Class<?> destination(boolean setupCompleted) {
        return setupCompleted ? HomeDashboardActivity.class : com.example.fintrack.app.setup.ui.SetupWelcomeActivity.class;
    }

    /**
     * Account exists and the password was correct, but the email is unverified. Resend needs the
     * account email: the backend looks it up by email only and silently ignores anything else.
     */
    private void promptResendVerification(String identifier) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.verify_required_title)
                        .setNegativeButton(R.string.verify_later, null);
        if (Patterns.EMAIL_ADDRESS.matcher(identifier).matches()) {
            builder.setMessage(R.string.verify_required_message)
                    .setPositiveButton(R.string.verify_resend, (dialog, which) -> resendVerification(identifier))
                    .show();
            return;
        }
        // Signed in with a username: ask for the registered email.
        EditText emailInput = new EditText(this);
        emailInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailInput.setHint(R.string.hint_email);
        FrameLayout container = new FrameLayout(this);
        int padding = getResources().getDimensionPixelSize(R.dimen.spacing_24);
        container.setPadding(padding, 0, padding, 0);
        container.addView(emailInput);
        AlertDialog dialog = builder.setMessage(R.string.verify_required_message_username)
                .setView(container)
                .setPositiveButton(R.string.verify_resend, null)
                .show();
        // Set after show() so an invalid email keeps the dialog open.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailInput.setError(getString(R.string.error_invalid_email));
                return;
            }
            dialog.dismiss();
            resendVerification(email);
        });
    }

    private void resendVerification(String email) {
        pendingVerificationEmail = email;
        authViewModel.resendVerification(email);
    }

    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_PENDING_VERIFICATION_EMAIL, pendingVerificationEmail);
    }

    /**
     * Kiểm tra tính hợp lệ (validate) của dữ liệu nhập vào và gọi hàm đăng nhập trong ViewModel
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
