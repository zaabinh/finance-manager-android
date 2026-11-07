package com.example.fintrack.app.setup.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.R;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.user.remote.FirstSetupRequest;
import com.example.fintrack.app.user.remote.UpdateNotificationSettingsRequest;
import com.example.fintrack.app.user.remote.UpdateProfileRequest;
import com.example.fintrack.app.user.viewmodel.UserViewModel;

// Màn hình thiết lập ví tiền/tài khoản (SetupWalletActivity) trong luồng cài đặt
public class SetupWalletActivity extends AppCompatActivity {

    // Khai báo các thành phần giao diện (UI)
    private ImageView ivBack;          // Nút Quay lại
    private TextView tvAddWallet;      // Thẻ/nút bấm để thêm ví mới
    private Button btnCompleteSetup;   // Nút hoàn tất cài đặt

    // Lưu trữ thông tin nhận được từ các màn hình trước (với giá trị mặc định phòng trường hợp không có dữ liệu)
    private String displayName = "Minh Anh";
    private String reminderTime = "20:00";
    private UserViewModel userViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_wallet); // Gắn giao diện XML vào Activity

        // Lấy dữ liệu (Tên và Giờ nhắc nhở) truyền từ các màn hình trước sang
        if (getIntent() != null) {
            if (getIntent().hasExtra(SetupBasicInfoActivity.EXTRA_DISPLAY_NAME)) {
                displayName = getIntent().getStringExtra(SetupBasicInfoActivity.EXTRA_DISPLAY_NAME);
            }
            if (getIntent().hasExtra(SetupNotificationActivity.EXTRA_REMINDER_TIME)) {
                reminderTime = getIntent().getStringExtra(SetupNotificationActivity.EXTRA_REMINDER_TIME);
            }
        }

        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);
        initViews();       // Ánh xạ các thành phần giao diện
        setupListeners();  // Gán sự kiện bấm cho các nút
        observeFirstSetup();
    }

    // Ánh xạ ID từ file XML sang biến Java
    private void initViews() {
        ivBack = findViewById(R.id.ivBack);
        tvAddWallet = findViewById(R.id.tvAddWallet);
        btnCompleteSetup = findViewById(R.id.btnCompleteSetup);
    }

    // Thiết lập xử lý sự kiện bấm cho các nút
    private void setupListeners() {
        // Nút Back: Đóng màn hình hiện tại để quay lại màn hình thông báo
        ivBack.setOnClickListener(v -> finish());

        // Bấm vào nút Thêm ví: Mở màn hình Thêm ví mới
        tvAddWallet.setOnClickListener(v -> {
            Intent intent = new Intent(SetupWalletActivity.this, com.example.fintrack.app.wallet.ui.WalletAddActivity.class);
            startActivity(intent);
        });

        // Nút Hoàn tất: gửi toàn bộ input của 3 bước trong một request
        btnCompleteSetup.setOnClickListener(v -> userViewModel.completeFirstSetup(buildRequest()));
    }

    // shortcut: chưa gửi ví, bổ sung trường wallets khi có wallet API
    private FirstSetupRequest buildRequest() {
        Intent in = getIntent();
        boolean notifEnabled = in.getBooleanExtra(SetupNotificationActivity.EXTRA_NOTIF_ENABLED, true);
        boolean budgetAlert = in.getBooleanExtra(SetupNotificationActivity.EXTRA_BUDGET_ALERT, true);
        return new FirstSetupRequest(
                new UpdateProfileRequest(displayName, in.getStringExtra(SetupBasicInfoActivity.EXTRA_TIME_ZONE)),
                new UpdateNotificationSettingsRequest(
                        notifEnabled,
                        in.getBooleanExtra(SetupNotificationActivity.EXTRA_DAILY_REMINDER, true),
                        reminderTime,
                        budgetAlert,
                        in.getIntExtra(SetupNotificationActivity.EXTRA_BUDGET_THRESHOLD, 80),
                        budgetAlert,
                        notifEnabled));
    }

    private void observeFirstSetup() {
        userViewModel.getFirstSetupResult().observe(this, resource -> {
            if (resource == null) return;
            btnCompleteSetup.setEnabled(resource.getStatus() != Resource.Status.LOADING);
            if (resource.getStatus() == Resource.Status.SUCCESS) {
                Intent intent = new Intent(SetupWalletActivity.this, SetupCompleteActivity.class);
                intent.putExtras(getIntent());
                // Đã lưu lên server: không cho Back quay lại các bước setup
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            } else if (resource.getStatus() == Resource.Status.ERROR) {
                Toast.makeText(this, resource.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}