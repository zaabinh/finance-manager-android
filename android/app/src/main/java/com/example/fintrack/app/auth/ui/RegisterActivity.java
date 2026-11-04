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

/**
 * Màn hình Đăng ký tài khoản (RegisterActivity).
 * Chịu trách nhiệm hiển thị giao diện nhập thông tin đăng ký, validate dữ liệu,
 * gọi ViewModel để gửi request lên server và điều hướng sang màn hình xác thực OTP nếu thành công.
 */
public class RegisterActivity extends AppCompatActivity {

    private static final String KEY_REGISTERED_EMAIL = "key_registered_email"; // Khóa để lưu tạm email khi xoay màn hình

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

    private boolean isPasswordVisible = false;          // Trạng thái hiển thị mật khẩu
    private boolean isConfirmPasswordVisible = false;   // Trạng thái hiển thị mật khẩu xác nhận
    private AuthViewModel authViewModel;                // ViewModel xử lý logic nghiệp vụ đăng ký
    private String registeredEmail = "";                // Lưu lại email vừa nhập

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth_register);

        // Khôi phục lại email nếu Activity bị hủy và tạo lại (ví dụ: xoay màn hình điện thoại)
        if (savedInstanceState != null) {
            registeredEmail = savedInstanceState.getString(KEY_REGISTERED_EMAIL, "");
        }

        // Khởi tạo ViewModel
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        initViews();       // Ánh xạ các View từ XML
        setupListeners();  // Cài đặt sự kiện click cho các nút
        setupObservers();  // Lắng nghe dữ liệu trả về từ ViewModel (LiveData)
    }

    /** Ánh xạ các thành phần giao diện từ file XML */
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

    /** Cài đặt các sự kiện tương tác (Click) */
    private void setupListeners() {
        // Sự kiện ẩn/hiện mật khẩu
        ivTogglePassword.setOnClickListener(v -> isPasswordVisible = PasswordToggleHelper.togglePasswordVisibility(etPassword, ivTogglePassword, isPasswordVisible));
        ivToggleConfirmPassword.setOnClickListener(v -> isConfirmPasswordVisible = PasswordToggleHelper.togglePasswordVisibility(etConfirmPassword, ivToggleConfirmPassword, isConfirmPasswordVisible));

        // Bấm nút Đăng ký
        btnRegister.setOnClickListener(v -> handleRegister());

        // Bấm chữ "Đăng nhập" để quay lại màn hình đăng nhập
        tvSignIn.setOnClickListener(v -> finish());
    }

    /** Lắng nghe trạng thái kết quả đăng ký từ ViewModel */
    private void setupObservers() {
        authViewModel.getRegisterResult().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.getStatus()) {
                case SUCCESS:
                    // Đăng ký thành công -> Bật lại nút, hiện thông báo và chuyển sang màn hình nhập mã OTP
                    btnRegister.setEnabled(true);
                    btnRegister.setText(R.string.btn_register);
                    Toast.makeText(this, R.string.register_success, Toast.LENGTH_SHORT).show();

                    // Lấy email đã đăng ký để truyền sang màn hình xác thực OTP
                    String emailToPass = authViewModel.getRegisteredEmail();
                    if (TextUtils.isEmpty(emailToPass)) {
                        emailToPass = registeredEmail;
                    }

                    Intent intent = new Intent(RegisterActivity.this, VerifyEmailActivity.class);
                    intent.putExtra("email", emailToPass);
                    startActivity(intent);
                    finish(); // Đóng màn hình đăng ký
                    break;

                case ERROR:
                    // Đăng ký thất bại -> Bật lại nút và hiển thị lỗi
                    btnRegister.setEnabled(true);
                    btnRegister.setText(R.string.btn_register);
                    String errorMsg = resource.getMessage() != null ? resource.getMessage() : getString(R.string.register_failed);
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    break;

                case LOADING:
                    // Đang xử lý đăng ký -> Khóa nút lại và đổi chữ thành "Đang tải..."
                    btnRegister.setEnabled(false);
                    btnRegister.setText(getString(R.string.loading_register));
                    break;
            }
        });
    }

    /** Kiểm tra tính hợp lệ của dữ liệu trước khi gửi request đăng ký */
    private void handleRegister() {
        String username = etUsername.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();

        // Kiểm tra tên đăng nhập không được để trống
        if (TextUtils.isEmpty(username)) {
            etUsername.setError(getString(R.string.error_required_field));
            etUsername.requestFocus();
            return;
        }

        // Kiểm tra email không được để trống
        if (TextUtils.isEmpty(email)) {
            etEmail.setError(getString(R.string.error_required_field));
            etEmail.requestFocus();
            return;
        }

        // Kiểm tra định dạng email hợp lệ
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError(getString(R.string.error_invalid_email));
            etEmail.requestFocus();
            return;
        }

        // Kiểm tra độ mạnh của mật khẩu
        String passwordError = PasswordValidator.validatePassword(this, password);
        if (passwordError != null) {
            etPassword.setError(passwordError);
            etPassword.requestFocus();
            return;
        }

        // Kiểm tra mật khẩu nhập lại có khớp không
        if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError(getString(R.string.error_password_not_match));
            etConfirmPassword.requestFocus();
            return;
        }

        // Kiểm tra người dùng đã đồng ý với điều khoản chưa
        if (!cbTerms.isChecked()) {
            Toast.makeText(this, R.string.error_agree_terms, Toast.LENGTH_SHORT).show();
            return;
        }

        // Dữ liệu hợp lệ -> Lưu email tạm và gọi hàm register trong ViewModel
        registeredEmail = email;
        authViewModel.register(username, email, password);
    }

    /** Lưu lại email vào Bundle khi Activity bị xoay chiều hoặc hệ thống kill tạm thời */
    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_REGISTERED_EMAIL, registeredEmail);
    }
}