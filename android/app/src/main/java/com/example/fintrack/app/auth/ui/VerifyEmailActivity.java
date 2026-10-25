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

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.R;
import com.example.fintrack.app.auth.viewmodel.AuthViewModel;

import java.util.Locale;

public class VerifyEmailActivity extends AppCompatActivity {

    private EditText etOtp1, etOtp2, etOtp3, etOtp4;
    private Button btnVerify;
    private TextView tvResendOtp;
    private CountDownTimer countDownTimer;
    private boolean isTimerRunning = false;
    private AuthViewModel authViewModel;
    private String userEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_email);

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        userEmail = getIntent().getStringExtra("email");
        if (userEmail == null) {
            userEmail = "";
        }

        initViews();
        setupOtpInputs();
        setupListeners();
        startResendTimer();
    }

    private void initViews() {
        etOtp1 = findViewById(R.id.etOtp1);
        etOtp2 = findViewById(R.id.etOtp2);
        etOtp3 = findViewById(R.id.etOtp3);
        etOtp4 = findViewById(R.id.etOtp4);
        btnVerify = findViewById(R.id.btnVerify);
        tvResendOtp = findViewById(R.id.tvResendOtp);
    }

    private void setupOtpInputs() {
        setupSingleBox(etOtp1, null, etOtp2);
        setupSingleBox(etOtp2, etOtp1, etOtp3);
        setupSingleBox(etOtp3, etOtp2, etOtp4);
        setupSingleBox(etOtp4, etOtp3, null);
    }

    private void setupSingleBox(EditText current, EditText previous, EditText next) {
        current.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() == 1) {
                    if (next != null) {
                        next.requestFocus();
                    }
                } else if (s.length() > 1) {
                    String pasted = s.toString().trim();
                    if (pasted.length() == 4) {
                        etOtp1.setText(String.valueOf(pasted.charAt(0)));
                        etOtp2.setText(String.valueOf(pasted.charAt(1)));
                        etOtp3.setText(String.valueOf(pasted.charAt(2)));
                        etOtp4.setText(String.valueOf(pasted.charAt(3)));
                        etOtp4.requestFocus();
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

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

    private void setupListeners() {
        btnVerify.setOnClickListener(v -> handleVerify());

        tvResendOtp.setOnClickListener(v -> {
            if (!isTimerRunning) {
                Toast.makeText(this, "Mã OTP mới đã được gửi!", Toast.LENGTH_SHORT).show();
                startResendTimer();
            }
        });
    }

    private void startResendTimer() {
        isTimerRunning = true;
        tvResendOtp.setEnabled(false);

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        countDownTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                tvResendOtp.setText(String.format(Locale.getDefault(), getString(R.string.resend_otp_countdown), seconds));
            }

            @Override
            public void onFinish() {
                isTimerRunning = false;
                tvResendOtp.setEnabled(true);
                tvResendOtp.setText(R.string.resend_otp_action);
            }
        }.start();
    }

    private void handleVerify() {
        String o1 = etOtp1.getText().toString().trim();
        String o2 = etOtp2.getText().toString().trim();
        String o3 = etOtp3.getText().toString().trim();
        String o4 = etOtp4.getText().toString().trim();

        if (TextUtils.isEmpty(o1) || TextUtils.isEmpty(o2) || TextUtils.isEmpty(o3) || TextUtils.isEmpty(o4)) {
            Toast.makeText(this, R.string.error_invalid_otp, Toast.LENGTH_SHORT).show();
            return;
        }

        String otp = o1 + o2 + o3 + o4;

        btnVerify.setEnabled(false);
        btnVerify.setText(getString(R.string.loading_verify));

        authViewModel.verifyEmail(userEmail, otp).observe(this, resource -> {
            switch (resource.getStatus()) {
                case SUCCESS:
                    btnVerify.setEnabled(true);
                    btnVerify.setText(R.string.btn_verify);
                    Toast.makeText(this, "Xác minh email thành công!", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(VerifyEmailActivity.this, com.example.fintrack.app.setup.SetupWelcomeActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                    break;

                case ERROR:
                    btnVerify.setEnabled(true);
                    btnVerify.setText(R.string.btn_verify);
                    String errorMsg = resource.getMessage() != null ? resource.getMessage() : "Xác thực thất bại";
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    break;

                case LOADING:
                    break;
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}
