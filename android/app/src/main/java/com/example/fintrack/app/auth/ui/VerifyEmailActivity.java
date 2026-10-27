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

public class VerifyEmailActivity extends AppCompatActivity {

    private static final String KEY_END_TIME_STAMP = "key_end_time_stamp";
    private static final String KEY_USER_EMAIL = "key_user_email";
    private static final long TIMER_DURATION_MS = 60000;

    private EditText etOtp1, etOtp2, etOtp3, etOtp4;
    private Button btnVerify;
    private TextView tvResendOtp;
    private CountDownTimer countDownTimer;
    private boolean isTimerRunning = false;
    private long endTimeStamp = 0;
    private boolean isFormattingOtp = false;

    private AuthViewModel authViewModel;
    private String userEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_email);

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        initViews();
        setupOtpInputs();
        setupListeners();
        setupObservers();

        if (savedInstanceState != null) {
            userEmail = savedInstanceState.getString(KEY_USER_EMAIL, getIntent().getStringExtra("email"));
            endTimeStamp = savedInstanceState.getLong(KEY_END_TIME_STAMP, 0);
            long remaining = endTimeStamp - System.currentTimeMillis();
            if (remaining > 0) {
                startResendTimer(remaining);
            } else {
                resetResendUi();
            }
        } else {
            userEmail = getIntent().getStringExtra("email");
            startResendTimer(TIMER_DURATION_MS);
        }

        if (userEmail == null) {
            userEmail = "";
        }
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
                if (isFormattingOtp) return;

                String rawInput = s.toString();
                String digits = rawInput.replaceAll("[^0-9]", "");

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

                if (digits.length() == 1) {
                    if (next != null) {
                        next.requestFocus();
                    }
                } else if (digits.length() > 1) {
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
        tvResendOtp.setOnClickListener(v -> handleResendOtp());
    }

    private void setupObservers() {
        authViewModel.getVerifyEmailResult().observe(this, resource -> {
            if (resource == null) return;
            switch (resource.getStatus()) {
                case SUCCESS:
                    btnVerify.setEnabled(true);
                    btnVerify.setText(R.string.btn_verify);
                    Toast.makeText(this, R.string.verify_email_success, Toast.LENGTH_SHORT).show();

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

        authViewModel.getResendOtpResult().observe(this, resource -> {
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

    private void handleResendOtp() {
        if (isTimerRunning) return;

        if (TextUtils.isEmpty(userEmail)) {
            Toast.makeText(this, R.string.error_required_field, Toast.LENGTH_SHORT).show();
            return;
        }

        authViewModel.resendOtp(userEmail);
    }

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
                resetResendUi();
            }
        }.start();
    }

    private void resetResendUi() {
        isTimerRunning = false;
        endTimeStamp = 0;
        tvResendOtp.setEnabled(true);
        tvResendOtp.setText(R.string.resend_otp_action);
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

        authViewModel.verifyEmail(userEmail, otp);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(KEY_END_TIME_STAMP, endTimeStamp);
        outState.putString(KEY_USER_EMAIL, userEmail);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}
