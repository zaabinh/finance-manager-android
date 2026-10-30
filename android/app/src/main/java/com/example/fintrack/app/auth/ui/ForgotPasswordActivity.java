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

/**
 * Màn hình Quên mật khẩu (ForgotPasswordActivity).
 * Chịu trách nhiệm cho phép người dùng nhập email đã đăng ký,
 * kiểm tra tính hợp lệ và gọi API gửi mã OTP khôi phục mật khẩu.
 */
public class ForgotPasswordActivity extends AppCompatActivity {

    private static final String KEY_REQUESTED_EMAIL = "key_requested_email"; // Khóa lưu tạm email khi xoay màn hình

    // Các thành phần giao diện (Ô nhập email, nút Gửi yêu cầu, chữ quay lại đăng nhập)
    private EditText etEmail;
    private Button btnSend;
    private TextView tvBackToLogin;

    private AuthViewModel authViewModel; // ViewModel xử lý nghiệp vụ quên mật khẩu
    private String requestedEmail = "";  // Lưu lại email vừa nhập

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth_forgot_password);

        // Khôi phục lại email nếu Activity bị hủy và tạo lại (ví dụ: khi xoay ngang/dọc điện thoại)
        if (savedInstanceState != null) {
            requestedEmail = savedInstanceState.getString(KEY_REQUESTED_EMAIL, "");
        }

        // Khởi tạo ViewModel
        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        initViews();       // Ánh xạ View từ XML
        setupListeners();  // Cài đặt sự kiện click cho các nút
        setupObservers();  // Lắng nghe kết quả trả về từ ViewModel
    }

    /** Ánh xạ các thành phần giao diện từ file XML */
    private void initViews() {
        etEmail = findViewById(R.id.etEmail);
        btnSend = findViewById(R.id.btnSend);
        tvBackToLogin = findViewById(R.id.tvBackToLogin);
    }

    /** Cài đặt các sự kiện tương tác bấm nút */
    private void setupListeners() {
        // Bấm nút Gửi mã
        btnSend.setOnClickListener(v -> handleSend());

        // Bấm chữ "Quay lại đăng nhập" để đóng màn hình này
        tvBackToLogin.setOnClickListener(v -> finish());
    }

    /** Lắng nghe kết quả trả về từ API quên mật khẩu thông qua LiveData trong ViewModel */
    private void setupObservers() {
        authViewModel.getForgotPasswordResult().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.getStatus()) {
                case SUCCESS:
                    // Gửi yêu cầu thành công -> Bật lại nút, thông báo và chuyển sang màn hình nhập OTP
                    btnSend.setEnabled(true);
                    btnSend.setText(R.string.btn_send);
                    Toast.makeText(this, R.string.forgot_password_success, Toast.LENGTH_SHORT).show();

                    // Lấy email để truyền sang màn hình xác thực OTP
                    String emailToPass = authViewModel.getForgotPasswordEmail();
                    if (TextUtils.isEmpty(emailToPass)) {
                        emailToPass = requestedEmail;
                    }

                    // Chuyển sang VerifyEmailActivity và đánh dấu cờ is_reset_password = true (để biết đây là luồng quên mật khẩu)
                    Intent intent = new Intent(ForgotPasswordActivity.this, VerifyEmailActivity.class);
                    intent.putExtra("email", emailToPass);
                    intent.putExtra("is_reset_password", true);
                    startActivity(intent);
                    finish(); // Đóng màn hình quên mật khẩu hiện tại
                    break;

                case ERROR:
                    // Gửi yêu cầu thất bại -> Bật lại nút và hiển thị lỗi
                    btnSend.setEnabled(true);
                    btnSend.setText(R.string.btn_send);
                    String errorMsg = resource.getMessage() != null ? resource.getMessage() : getString(R.string.forgot_password_failed);
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    break;

                case LOADING:
                    // Đang xử lý gọi API -> Khóa nút lại và đổi chữ thành trạng thái đang tải
                    btnSend.setEnabled(false);
                    btnSend.setText(getString(R.string.loading_send_code));
                    break;
            }
        });
    }

    /** Kiểm tra tính hợp lệ của email trước khi gửi yêu cầu quên mật khẩu */
    private void handleSend() {
        String email = etEmail.getText().toString().trim();

        // Kiểm tra xem ô email có bị để trống không
        if (TextUtils.isEmpty(email)) {
            etEmail.setError(getString(R.string.error_required_field));
            etEmail.requestFocus();
            return;
        }

        // Kiểm tra xem email có đúng định dạng không
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError(getString(R.string.error_invalid_email));
            etEmail.requestFocus();
            return;
        }

        // Dữ liệu hợp lệ -> Lưu lại email và gọi hàm forgotPassword trong ViewModel
        requestedEmail = email;
        authViewModel.forgotPassword(email);
    }

    /** Lưu lại email vào Bundle khi Activity bị xoay chiều hoặc hệ thống kill tạm thời */
    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_REQUESTED_EMAIL, requestedEmail);
    }
}