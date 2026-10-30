package com.example.fintrack.app.auth.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.R;
import com.example.fintrack.app.auth.util.PasswordToggleHelper;
import com.example.fintrack.app.auth.util.PasswordValidator;
import com.example.fintrack.app.auth.viewmodel.AuthViewModel;

/**
 * Màn hình Đặt lại mật khẩu mới (ChangePasswordActivity).
 * Cho phép người dùng nhập mật khẩu mới và xác nhận lại mật khẩu,
 * sau đó gửi yêu cầu lên server để hoàn tất quá trình khôi phục tài khoản.
 */
public class ChangePasswordActivity extends AppCompatActivity {

    // Các khóa để lưu tạm email và mã OTP khi xoay màn hình
    private static final String KEY_EMAIL = "key_email";
    private static final String KEY_OTP = "key_otp";

    // Các thành phần giao diện (Ô nhập mật khẩu mới, xác nhận mật khẩu, nút bấm, icon ẩn/hiện mật khẩu)
    private EditText etNewPassword;
    private EditText etConfirmNewPassword;
    private ImageView ivToggleNewPassword;
    private ImageView ivToggleConfirmNewPassword;
    private Button btnSubmit;

    private boolean isNewPasswordVisible = false;        // Trạng thái hiện/ẩn mật khẩu mới
    private boolean isConfirmNewPasswordVisible = false; // Trạng thái hiện/ẩn mật khẩu xác nhận

    private AuthViewModel authViewModel; // ViewModel xử lý nghiệp vụ đặt lại mật khẩu
    private String email;                // Email nhận từ màn hình trước
    private String otp;                  // Mã OTP xác thực nhận từ màn hình trước

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth_change_password);

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        // Khôi phục lại email và OTP nếu Activity bị hủy và tạo lại (ví dụ: khi xoay ngang/dọc màn hình)
        if (savedInstanceState != null) {
            email = savedInstanceState.getString(KEY_EMAIL, getIntent().getStringExtra("email"));
            otp = savedInstanceState.getString(KEY_OTP, getIntent().getStringExtra("otp"));
        } else {
            // Lấy email và OTP được truyền qua Intent từ màn hình VerifyEmailActivity
            email = getIntent().getStringExtra("email");
            otp = getIntent().getStringExtra("otp");
        }

        if (email == null) email = "";
        if (otp == null) otp = "";

        // Nếu OTP trống, lấy dự phòng từ ViewModel
        if (TextUtils.isEmpty(otp)) {
            otp = authViewModel.getResetOtp();
        }

        initViews();       // Ánh xạ View từ XML
        setupListeners();  // Cài đặt sự kiện click cho các nút
        setupObservers();  // Lắng nghe kết quả trả về từ ViewModel
    }

    /** Ánh xạ các thành phần giao diện từ file XML */
    private void initViews() {
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmNewPassword = findViewById(R.id.etConfirmNewPassword);
        ivToggleNewPassword = findViewById(R.id.ivToggleNewPassword);
        ivToggleConfirmNewPassword = findViewById(R.id.ivToggleConfirmNewPassword);
        btnSubmit = findViewById(R.id.btnSubmit);
    }

    /** Cài đặt các sự kiện tương tác bấm nút và ẩn/hiện mật khẩu */
    private void setupListeners() {
        // Sự kiện ẩn/hiện mật khẩu mới
        ivToggleNewPassword.setOnClickListener(v -> isNewPasswordVisible = PasswordToggleHelper.togglePasswordVisibility(etNewPassword, ivToggleNewPassword, isNewPasswordVisible));

        // Sự kiện ẩn/hiện mật khẩu xác nhận
        ivToggleConfirmNewPassword.setOnClickListener(v -> isConfirmNewPasswordVisible = PasswordToggleHelper.togglePasswordVisibility(etConfirmNewPassword, ivToggleConfirmNewPassword, isConfirmNewPasswordVisible));

        // Bấm nút Xác nhận đổi mật khẩu
        btnSubmit.setOnClickListener(v -> handleSubmit());
    }

    /** Lắng nghe kết quả trả về từ API đặt lại mật khẩu thông qua LiveData */
    private void setupObservers() {
        authViewModel.getResetPasswordResult().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.getStatus()) {
                case SUCCESS:
                    // Đặt lại mật khẩu thành công -> Bật lại nút, thông báo và chuyển về màn hình đăng nhập
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText(R.string.btn_submit);
                    Toast.makeText(this, R.string.reset_password_success, Toast.LENGTH_SHORT).show();

                    // Chuyển về LoginActivity và xóa sạch lịch sử màn hình cũ
                    Intent intent = new Intent(ChangePasswordActivity.this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                    break;

                case ERROR:
                    // Thất bại -> Bật lại nút và hiển thị thông báo lỗi
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText(R.string.btn_submit);
                    String errorMsg = resource.getMessage() != null ? resource.getMessage() : getString(R.string.reset_password_failed);
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    break;

                case LOADING:
                    // Đang xử lý gọi API -> Khóa nút lại và đổi chữ thành "Đang xử lý..."
                    btnSubmit.setEnabled(false);
                    btnSubmit.setText(getString(R.string.loading_processing));
                    break;
            }
        });
    }

    /** Kiểm tra tính hợp lệ của mật khẩu mới trước khi gửi request lên server */
    private void handleSubmit() {
        String newPassword = etNewPassword.getText().toString();
        String confirmNewPassword = etConfirmNewPassword.getText().toString();

        // Kiểm tra độ mạnh của mật khẩu mới theo quy định
        String passwordError = PasswordValidator.validatePassword(this, newPassword);
        if (passwordError != null) {
            etNewPassword.setError(passwordError);
            etNewPassword.requestFocus();
            return;
        }

        // Kiểm tra xem mật khẩu nhập lại có khớp với mật khẩu mới không
        if (!newPassword.equals(confirmNewPassword)) {
            etConfirmNewPassword.setError(getString(R.string.error_password_not_match));
            etConfirmNewPassword.requestFocus();
            return;
        }

        // Dữ liệu hợp lệ -> Gọi hàm resetPassword trong ViewModel kèm theo email, otp và mật khẩu mới
        authViewModel.resetPassword(email, otp, newPassword);
    }

    /** Lưu lại email và OTP vào Bundle khi Activity bị xoay chiều màn hình */
    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_EMAIL, email);
        outState.putString(KEY_OTP, otp);
    }
}