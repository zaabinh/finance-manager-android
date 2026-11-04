package com.example.fintrack.app.settings.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.R;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.SessionManager;
import com.example.fintrack.app.home.ui.HomeDashboardActivity;
import com.example.fintrack.app.user.viewmodel.UserViewModel;

// Màn hình Cài đặt ứng dụng (SettingActivity)
public class SettingActivity extends AppCompatActivity {

    // Khai báo các thành phần giao diện
    private LinearLayout layoutProfile;
    private TextView tvAvatarInitials;
    private TextView tvProfileName;
    private TextView tvProfileEmail;
    private ImageButton btnEditProfile;

    private View rowCurrency;
    private TextView tvCurrencyValue;
    private View rowTimezone;
    private TextView tvTimezoneValue;

    private TextView rowNotification;
    private TextView rowWallets;
    private TextView rowCategories;
    private TextView rowSecurity;
    private View rowAboutApp;

    // Thanh điều hướng dưới đáy
    private ImageView navHome;
    private SessionManager sessionManager;
    private UserViewModel userViewModel;

    private String currentCurrency = "VND (₫)";
    private String currentTimezone = "Hanoi GMT+7";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        sessionManager = SessionManager.getInstance(getApplicationContext());
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);

        initViews();
        loadUserData();
        setupObservers();
        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserData();
        if (userViewModel != null) {
            userViewModel.fetchProfile();
        }
    }

    private void initViews() {
        layoutProfile = findViewById(R.id.layoutProfile);
        tvAvatarInitials = findViewById(R.id.tvAvatarInitials);
        tvProfileName = findViewById(R.id.tvProfileName);
        tvProfileEmail = findViewById(R.id.tvProfileEmail);
        btnEditProfile = findViewById(R.id.btnEditProfile);

        rowCurrency = findViewById(R.id.rowCurrency);
        tvCurrencyValue = findViewById(R.id.tvCurrencyValue);
        rowTimezone = findViewById(R.id.rowTimezone);
        tvTimezoneValue = findViewById(R.id.tvTimezoneValue);

        rowNotification = findViewById(R.id.rowNotification);
        rowWallets = findViewById(R.id.rowWallets);
        rowCategories = findViewById(R.id.rowCategories);
        rowSecurity = findViewById(R.id.rowSecurity);
        rowAboutApp = findViewById(R.id.rowAboutApp);

        navHome = findViewById(R.id.navHome);
    }

    private void loadUserData() {
        String name = sessionManager.getUserName();
        String email = sessionManager.getUserEmail();

        if (name != null && !name.isEmpty()) {
            tvProfileName.setText(name);
            tvAvatarInitials.setText(getInitials(name));
        }
        if (email != null && !email.isEmpty()) {
            tvProfileEmail.setText(email);
        }
    }

    private String getInitials(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return "MA";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    private void setupObservers() {
        userViewModel.getProfileResult().observe(this, resource -> {
            if (resource != null && resource.getStatus() == Resource.Status.SUCCESS && resource.getData() != null) {
                String name = resource.getData().getName();
                String email = resource.getData().getEmail();
                if (name != null) {
                    tvProfileName.setText(name);
                    tvAvatarInitials.setText(getInitials(name));
                }
                if (email != null) tvProfileEmail.setText(email);
                sessionManager.saveUserProfile(resource.getData().getUserId(), email, name);
            }
        });
    }

    private void setupListeners() {
        View.OnClickListener openProfileListener = v -> {
            Intent intent = new Intent(SettingActivity.this, SettingUserActivity.class);
            startActivity(intent);
        };

        layoutProfile.setOnClickListener(openProfileListener);
        btnEditProfile.setOnClickListener(openProfileListener);

        // Đơn vị tiền tệ
        if (rowCurrency != null) {
            rowCurrency.setOnClickListener(v -> {
                String[] currencies = {"VND (₫)", "USD ($)", "EUR (€)"};
                new AlertDialog.Builder(this)
                        .setTitle("Chọn đơn vị tiền tệ")
                        .setItems(currencies, (dialog, which) -> {
                            currentCurrency = currencies[which];
                            tvCurrencyValue.setText(currentCurrency + "  ›");
                            Toast.makeText(this, "Đã đổi đơn vị tiền tệ: " + currentCurrency, Toast.LENGTH_SHORT).show();
                        })
                        .show();
            });
        }

        // Múi giờ
        if (rowTimezone != null) {
            rowTimezone.setOnClickListener(v -> {
                String[] timezones = {"Hanoi GMT+7", "Tokyo GMT+9", "London GMT+0", "New York GMT-5"};
                new AlertDialog.Builder(this)
                        .setTitle("Chọn múi giờ")
                        .setItems(timezones, (dialog, which) -> {
                            currentTimezone = timezones[which];
                            tvTimezoneValue.setText(currentTimezone + "  ›");
                            Toast.makeText(this, "Đã đổi múi giờ: " + currentTimezone, Toast.LENGTH_SHORT).show();
                        })
                        .show();
            });
        }

        // Cấu hình thông báo
        rowNotification.setOnClickListener(v -> {
            Intent intent = new Intent(SettingActivity.this, SettingNotificationActivity.class);
            startActivity(intent);
        });

        // Quản lý Ví
        rowWallets.setOnClickListener(v -> {
            Intent intent = new Intent(SettingActivity.this, com.example.fintrack.app.wallet.ui.WalletListActivity.class);
            startActivity(intent);
        });

        // Quản lý Danh mục
        rowCategories.setOnClickListener(v -> {
            Intent intent = new Intent(SettingActivity.this, com.example.fintrack.app.category.ui.CategoryListActivity.class);
            startActivity(intent);
        });

        // Bảo mật & Tài khoản
        rowSecurity.setOnClickListener(v -> {
            Intent intent = new Intent(SettingActivity.this, SettingSecurityActivity.class);
            startActivity(intent);
        });

        // Thông tin ứng dụng
        if (rowAboutApp != null) {
            rowAboutApp.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Đồng - Quản lý tài chính")
                        .setMessage("Phiên bản: v1.0.0\n\nNgười bạn đồng hành tài chính cá nhân của bạn. Giúp bạn theo dõi số dư, ghi chép giao dịch, đặt ngân sách và phân tích tài chính hiệu quả.")
                        .setPositiveButton("Đóng", (dialog, which) -> dialog.dismiss())
                        .show();
            });
        }

        // Bottom Navigation & Speed Dial FAB
        com.example.fintrack.app.finance.ui.FinanceUi.navigation(this, R.id.navSetting);
    }
}
