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

/**
 * Màn hình đăng nhập người dùng.
 */
public class LoginActivity extends AppCompatActivity {

    // Khai báo các thành phần giao diện (UI Elements)
    private EditText etUsernameOrEmail;
    private EditText etPassword;
    private ImageView ivTogglePassword;
    private TextView tvForgotPassword;
    private Button btnLogin;
    private TextView tvSignUp;

    // Khai báo trạng thái và các thành phần xử lý logic
    private boolean isPasswordVisible = false; // Trạng thái hiển thị mật khẩu (ẩn/hiện)
    private AuthViewModel authViewModel;       // ViewModel xử lý logic đăng nhập
    private SessionManager sessionManager;     // Quản lý phiên đăng nhập của người dùng

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Kiểm tra xem người dùng đã đăng nhập từ trước đó chưa
        sessionManager = SessionManager.getInstance(getApplicationContext());
        if (sessionManager.isLoggedIn()) {
            // Nếu đã đăng nhập, chuyển hướng đến màn hình chính hoặc màn hình thiết lập ban đầu
            Class<?> nextActivity = sessionManager.isSetupCompleted()
                    ? HomeDashboardActivity.class
                    : com.example.fintrack.app.setup.ui.SetupWelcomeActivity.class;

            Intent intent = new Intent(this, nextActivity);
            startActivity(intent);
            finish(); // Kết thúc Activity hiện tại để không quay lại màn hình đăng nhập khi nhấn Back
            return;
        }

        // Gắn layout giao diện cho Activity
        setContentView(R.layout.activity_auth_login);

        // Khởi tạo AuthViewModel để kết nối dữ liệu
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        // Gọi các hàm thiết lập giao diện và sự kiện
        initViews();
        setupListeners();
        setupObservers();
    }

    /**
     * Ánh xạ các View từ file XML layout sang mã Java
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
     * Thiết lập sự kiện tương tác (click) cho các nút và văn bản điều hướng
     */
    private void setupListeners() {
        // Sự kiện ẩn/hiện mật khẩu khi bấm vào biểu tượng con mắt
        ivTogglePassword.setOnClickListener(v -> isPasswordVisible = PasswordToggleHelper.togglePasswordVisibility(etPassword, ivTogglePassword, isPasswordVisible));

        // Sự kiện bấm nút Đăng nhập
        btnLogin.setOnClickListener(v -> handleLogin());

        // Sự kiện bấm vào "Quên mật khẩu" -> Chuyển sang màn hình khôi phục mật khẩu
        tvForgotPassword.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
            startActivity(intent);
        });

        // Sự kiện bấm vào "Đăng ký" -> Chuyển sang màn hình tạo tài khoản mới
        tvSignUp.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }

    /**
     * Lắng nghe các thay đổi trạng thái trả về từ ViewModel (Thành công, Thất bại, Đang tải)
     */
    private void setupObservers() {
        authViewModel.getLoginResult().observe(this, resource -> {
            if (resource == null) return;

            switch (resource.getStatus()) {
                case SUCCESS:
                    // Bật lại nút đăng nhập và khôi phục text ban đầu khi có kết quả
                    btnLogin.setEnabled(true);
                    btnLogin.setText(R.string.btn_login);

                    // Kiểm tra dữ liệu trả về hợp lệ và có chứa Token xác thực không
                    if (resource.getData() != null && !TextUtils.isEmpty(resource.getData().getAccessToken())) {
                        // Lưu lại token phiên làm việc
                        sessionManager.saveAuthToken(
                                resource.getData().getAccessToken(),
                                resource.getData().getRefreshToken()
                        );
                        // Lưu thông tin cá nhân cơ bản của người dùng
                        sessionManager.saveUserProfile(
                                resource.getData().getUserId(),
                                resource.getData().getEmail(),
                                resource.getData().getName()
                        );
                        // Thông báo đăng nhập thành công
                        Toast.makeText(this, R.string.login_success, Toast.LENGTH_SHORT).show();

                        // Điều hướng dựa vào việc tài khoản đã hoàn tất thiết lập ban đầu chưa
                        Class<?> nextActivity = sessionManager.isSetupCompleted() ? HomeDashboardActivity.class : com.example.fintrack.app.setup.ui.SetupWelcomeActivity.class;
                        Intent intent = new Intent(LoginActivity.this, nextActivity);
                        startActivity(intent);
                        finish(); // Kết thúc màn hình đăng nhập
                    } else {
                        // Báo lỗi nếu dữ liệu trả về rỗng hoặc không hợp lệ
                        Toast.makeText(this, R.string.login_response_invalid, Toast.LENGTH_LONG).show();
                    }
                    break;

                case ERROR:
                    // Đăng nhập thất bại: Khôi phục nút bấm và hiển thị thông báo lỗi
                    btnLogin.setEnabled(true);
                    btnLogin.setText(R.string.btn_login);
                    String errorMsg = resource.getMessage() != null ? resource.getMessage() : getString(R.string.login_failed);

                    if ("AUTH_EMAIL_NOT_VERIFIED".equals(errorMsg)) {
                        String userEmail = etUsernameOrEmail.getText().toString().trim();
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                                .setTitle("Tài khoản chưa xác thực Email")
                                .setMessage("Tài khoản của bạn chưa được kích hoạt. Bạn có muốn gửi lại mã xác nhận OTP về email không?")
                                .setPositiveButton("Gửi lại Email", (dialog, which) -> authViewModel.resendVerification(userEmail))
                                .setNegativeButton("Để sau", null)
                                .show();
                    } else {
                        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    }
                    break;

                case LOADING:
                    // Đang trong quá trình xử lý đăng nhập: Vô hiệu hóa nút và đổi text thành "Đang tải..."
                    btnLogin.setEnabled(false);
                    btnLogin.setText(getString(R.string.loading_login));
                    break;
            }
        });

        // Lắng nghe kết quả gửi lại email xác nhận tài khoản (/resend-verification)
        authViewModel.getResendVerificationResult().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.getStatus()) {
                case SUCCESS:
                    Toast.makeText(this, "Mã xác nhận đã được gửi! Vui lòng kiểm tra email.", Toast.LENGTH_LONG).show();
                    String email = etUsernameOrEmail.getText().toString().trim();
                    authViewModel.setRegisteredEmail(email);
                    Intent intent = new Intent(LoginActivity.this, VerifyEmailActivity.class);
                    startActivity(intent);
                    break;
                case ERROR:
                    String msg = resource.getMessage() != null ? resource.getMessage() : "Không thể gửi email xác nhận. Vui lòng thử lại.";
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    break;
                case LOADING:
                    Toast.makeText(this, "Đang gửi lại email xác nhận...", Toast.LENGTH_SHORT).show();
                    break;
            }
        });
    }

    /**
     * Kiểm tra tính hợp lệ (validate) của dữ liệu nhập vào và gọi hàm đăng nhập trong ViewModel
     */
    private void handleLogin() {
        String usernameOrEmail = etUsernameOrEmail.getText().toString().trim();
        String password = etPassword.getText().toString();

        // Kiểm tra xem tên đăng nhập hoặc email có bị để trống không
        if (TextUtils.isEmpty(usernameOrEmail)) {
            etUsernameOrEmail.setError(getString(R.string.error_required_field));
            etUsernameOrEmail.requestFocus();
            return;
        }

        // Kiểm tra xem mật khẩu có bị để trống không
        if (TextUtils.isEmpty(password)) {
            etPassword.setError(getString(R.string.error_required_field));
            etPassword.requestFocus();
            return;
        }

        // Nếu dữ liệu hợp lệ, gọi phương thức xử lý đăng nhập từ ViewModel
        authViewModel.login(usernameOrEmail, password);
    }
}