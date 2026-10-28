package com.example.fintrack.app.auth.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.R;
import com.example.fintrack.app.auth.viewmodel.AuthViewModel;

import java.util.Locale;

/**
 * Màn hình Nhập mã xác thực OTP (VerifyEmailActivity).
 * Dùng cho cả 2 trường hợp:
 * 1. Xác thực email sau khi đăng ký tài khoản mới.
 * 2. Nhận mã OTP để đặt lại mật khẩu mới khi quên mật khẩu.
 */
public class VerifyEmailActivity extends AppCompatActivity {

    // Các khóa để lưu trạng thái tạm thời khi xoay màn hình
    private static final String KEY_END_TIME_STAMP = "key_end_time_stamp";
    private static final String KEY_USER_EMAIL = "key_user_email";
    private static final String KEY_IS_RESET_PASSWORD = "key_is_reset_password";
    private static final long TIMER_DURATION_MS = 60000; // Thời gian đếm ngược gửi lại OTP: 60 giây

    // Các thành phần giao diện (4 ô nhập OTP, nút xác thực, chữ gửi lại mã)
    private EditText etOtp1, etOtp2, etOtp3, etOtp4;
    private Button btnVerify;
    private TextView tvResendOtp;

    private CountDownTimer countDownTimer; // Bộ đếm ngược thời gian gửi lại OTP
    private boolean isTimerRunning = false;
    private long endTimeStamp = 0;
    private boolean isFormattingOtp = false;
    private boolean isResetPassword = false; // Cờ phân biệt: true = Quên mật khẩu, false = Xác thực đăng ký

    private AuthViewModel authViewModel;
    private String userEmail; // Email của người dùng nhận mã OTP

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth_verify_email);

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        initViews();       // Ánh xạ View
        setupOtpInputs();  // Cài đặt hành vi thông minh cho 4 ô nhập OTP (tự nhảy ô, dán mã...)
        setupListeners();  // Cài đặt sự kiện bấm nút
        setupObservers();  // Lắng nghe kết quả trả về từ ViewModel

        // Khôi phục lại trạng thái cũ nếu Activity bị hủy và tạo lại (ví dụ: xoay màn hình)
        if (savedInstanceState != null) {
            isResetPassword = savedInstanceState.getBoolean(KEY_IS_RESET_PASSWORD, false);
            userEmail = savedInstanceState.getString(KEY_USER_EMAIL, getIntent().getStringExtra("email"));
            endTimeStamp = savedInstanceState.getLong(KEY_END_TIME_STAMP, 0);
            long remaining = endTimeStamp - System.currentTimeMillis();
            if (remaining > 0) {
                startResendTimer(remaining); // Tiếp tục chạy đếm ngược thời gian còn lại
            } else {
                resetResendUi();
            }
        } else {
            // Lấy dữ liệu truyền từ màn hình trước sang
            isResetPassword = getIntent().getBooleanExtra("is_reset_password", false);
            userEmail = getIntent().getStringExtra("email");
            startResendTimer(TIMER_DURATION_MS); // Bắt đầu đếm ngược 60s lần đầu
        }

        if (userEmail == null) {
            userEmail = "";
        }
    }

    /** Ánh xạ các thành phần giao diện từ XML */
    private void initViews() {
        etOtp1 = findViewById(R.id.etOtp1);
        etOtp2 = findViewById(R.id.etOtp2);
        etOtp3 = findViewById(R.id.etOtp3);
        etOtp4 = findViewById(R.id.etOtp4);
        btnVerify = findViewById(R.id.btnVerify);
        tvResendOtp = findViewById(R.id.tvResendOtp);
    }

    /** Liên kết 4 ô nhập OTP để tự động chuyển focus qua lại khi nhập hoặc xóa */
    private void setupOtpInputs() {
        setupSingleBox(etOtp1, null, etOtp2);
        setupSingleBox(etOtp2, etOtp1, etOtp3);
        setupSingleBox(etOtp3, etOtp2, etOtp4);
        setupSingleBox(etOtp4, etOtp3, null);
    }

    /** Xử lý logic thông minh cho từng ô nhập OTP (tự nhảy sang ô tiếp theo, hỗ trợ dán 4 số...) */
    private void setupSingleBox(EditText current, EditText previous, EditText next) {
        current.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isFormattingOtp) return;

                String rawInput = s.toString();
                String digits = rawInput.replaceAll("[^0-9]", ""); // Chỉ lấy số

                // Nếu người dùng dán (paste) nguyên một mã 4 số vào ô đầu tiên
                if (digits.length() >= 4) {
                    isFormattingOtp = true;
                    etOtp1.setText(String.valueOf(digits.charAt(0)));
                    etOtp2.setText(String.valueOf(digits.charAt(1)));
                    etOtp3.setText(String.valueOf(digits.charAt(2)));
                    etOtp4.setText(String.valueOf(digits.charAt(3)));
                    etOtp4.requestFocus();
                    etOtp4.setSelection(etOtp4.getText().length());
                    isFormattingOtp = false;
                    return;
                }

                // Nếu nhập 1 số -> tự động nhảy sang ô tiếp theo (next)
                if (digits.length() == 1) {
                    if (next != null) {
                        next.requestFocus();
                    }
                }
                else if (digits.length() > 1) {
                    isFormattingOtp = true;
                    char lastChar = digits.charAt(digits.length() - 1);
                    current.setText(String.valueOf(lastChar));
                    current.setSelection(current.getText().length());
                    if (next != null) {
                        next.requestFocus();
                    }
                    isFormattingOtp = false;
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Xử lý khi bấm nút xóa (Backspace): tự động lùi về ô trước đó nếu ô hiện tại đang trống
        current.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN) {
                if (TextUtils.isEmpty(current.getText().toString()) && previous != null) {
                    previous.requestFocus();
                    previous.setText("");
                    return true;
                }
            }
            return false;
        });
    }

    /** Cài đặt sự kiện bấm nút Xác thực và nút Gửi lại mã */
    private void setupListeners() {
        btnVerify.setOnClickListener(v -> handleVerify());
        tvResendOtp.setOnClickListener(v -> handleResendOtp());
    }

    /** Lắng nghe kết quả trả về từ ViewModel cho các API (Xác thực email, Gửi lại OTP) */
    private void setupObservers() {
        // Kết quả xác thực email đăng ký
        authViewModel.getVerifyEmailResult().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.getStatus()) {
                case SUCCESS:
                    btnVerify.setEnabled(true);
                    btnVerify.setText(R.string.btn_verify);
                    Toast.makeText(this, R.string.verify_email_success, Toast.LENGTH_SHORT).show();

                    // Thành công -> Chuyển về màn hình Đăng nhập và xóa sạch lịch sử màn hình cũ
                    Intent intent = new Intent(VerifyEmailActivity.this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                    break;

                case ERROR:
                    btnVerify.setEnabled(true);
                    btnVerify.setText(R.string.btn_verify);
                    String errorMsg = resource.getMessage() != null ? resource.getMessage() : getString(R.string.verify_email_failed);
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    break;

                case LOADING:
                    btnVerify.setEnabled(false);
                    btnVerify.setText(getString(R.string.loading_verify));
                    break;
            }
        });

        // Kết quả gửi lại OTP (cho trường hợp đăng ký)
        authViewModel.getResendOtpResult().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.getStatus()) {
                case SUCCESS:
                    Toast.makeText(this, R.string.resend_otp_success, Toast.LENGTH_SHORT).show();
                    startResendTimer(TIMER_DURATION_MS); // Chạy lại đồng hồ đếm ngược 60s
                    break;

                case ERROR:
                    resetResendUi();
                    String errorMsg = resource.getMessage() != null ? resource.getMessage() : getString(R.string.resend_otp_failed);
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    break;

                case LOADING:
                    tvResendOtp.setEnabled(false);
                    tvResendOtp.setText(R.string.loading_send_code);
                    break;
            }
        });

        // Kết quả gửi lại OTP (cho trường hợp quên mật khẩu)
        authViewModel.getForgotPasswordResult().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.getStatus()) {
                case SUCCESS:
                    Toast.makeText(this, R.string.resend_otp_success, Toast.LENGTH_SHORT).show();
                    startResendTimer(TIMER_DURATION_MS);
                    break;

                case ERROR:
                    resetResendUi();
                    String errorMsg = resource.getMessage() != null ? resource.getMessage() : getString(R.string.resend_otp_failed);
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    break;

                case LOADING:
                    tvResendOtp.setEnabled(false);
                    tvResendOtp.setText(R.string.loading_send_code);
                    break;
            }
        });
    }

    /** Xử lý hành động bấm Gửi lại mã OTP */
    private void handleResendOtp() {
        if (isTimerRunning) return; // Nếu đang trong thời gian đếm ngược thì không cho bấm

        if (TextUtils.isEmpty(userEmail)) {
            Toast.makeText(this, R.string.error_required_field, Toast.LENGTH_SHORT).show();
            return;
        }

        // Tùy theo luồng (Quên mật khẩu hay Đăng ký) mà gọi hàm API tương ứng
        if (isResetPassword) {
            authViewModel.forgotPassword(userEmail);
        } else {
            authViewModel.resendOtp(userEmail);
        }
    }

    /** Bắt đầu chạy đồng hồ đếm ngược thời gian chờ gửi lại OTP (ví dụ: 60 giây) */
    private void startResendTimer(long durationMillis) {
        isTimerRunning = true;
        tvResendOtp.setEnabled(false);
        endTimeStamp = System.currentTimeMillis() + durationMillis;

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        countDownTimer = new CountDownTimer(durationMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                tvResendOtp.setText(String.format(Locale.getDefault(), getString(R.string.resend_otp_countdown), seconds));
            }

            @Override
            public void onFinish() {
                resetResendUi(); // Hết giờ -> Bật lại nút "Gửi lại mã"
            }
        }.start();
    }

    /** Đưa giao diện nút Gửi lại mã về trạng thái ban đầu (có thể bấm được) */
    private void resetResendUi() {
        isTimerRunning = false;
        endTimeStamp = 0;
        tvResendOtp.setEnabled(true);
        tvResendOtp.setText(R.string.resend_otp_action);
    }

    /** Xử lý khi người dùng nhập đủ 4 số OTP và bấm Xác thực */
    private void handleVerify() {
        String o1 = etOtp1.getText().toString().trim();
        String o2 = etOtp2.getText().toString().trim();
        String o3 = etOtp3.getText().toString().trim();
        String o4 = etOtp4.getText().toString().trim();

        // Kiểm tra xem đã điền đủ 4 ô chưa
        if (TextUtils.isEmpty(o1) || TextUtils.isEmpty(o2) || TextUtils.isEmpty(o3) || TextUtils.isEmpty(o4)) {
            Toast.makeText(this, R.string.error_invalid_otp, Toast.LENGTH_SHORT).show();
            return;
        }

        String otp = o1 + o2 + o3 + o4; // Ghép 4 số thành mã OTP hoàn chỉnh

        // Nếu là luồng Quên mật khẩu -> Lưu OTP và chuyển sang màn hình Đổi mật khẩu mới
        if (isResetPassword) {
            authViewModel.setResetOtp(otp);
            Intent intent = new Intent(VerifyEmailActivity.this, ChangePasswordActivity.class);
            intent.putExtra("email", userEmail);
            intent.putExtra("otp", otp);
            startActivity(intent);
            finish();
        }
        // Nếu là luồng Đăng ký thông thường -> Gọi API xác thực email ngay
        else {
            authViewModel.verifyEmail(userEmail, otp);
        }
    }

    /** Lưu lại các trạng thái quan trọng khi Activity bị xoay chiều màn hình */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_IS_RESET_PASSWORD, isResetPassword);
        outState.putLong(KEY_END_TIME_STAMP, endTimeStamp);
        outState.putString(KEY_USER_EMAIL, userEmail);
    }

    /** Hủy bộ đếm ngược khi Activity bị hủy để tránh rò rỉ bộ nhớ (Memory Leak) */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}