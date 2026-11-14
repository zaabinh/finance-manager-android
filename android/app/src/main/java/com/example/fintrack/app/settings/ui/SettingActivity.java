package com.example.fintrack.app.settings.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.example.fintrack.R;
import com.example.fintrack.databinding.ActivitySettingBinding;
import com.example.fintrack.app.FintrackApplication;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.SessionSnapshot;
import com.example.fintrack.app.core.session.SessionStore;
import com.example.fintrack.app.user.viewmodel.UserViewModel;

// Màn hình Cài đặt ứng dụng (SettingActivity)
public class SettingActivity extends AppCompatActivity {

    private ActivitySettingBinding binding;

    // Khai báo các thành phần giao diện
    private LinearLayout layoutProfile;
    private TextView tvAvatarInitials;
    private TextView tvProfileName;
    private TextView tvProfileEmail;
    private ImageButton btnEditProfile;

    private TextView rowNotification;
    private TextView rowWallets;
    private TextView rowCategories;
    private TextView rowSecurity;
    private View rowAboutApp;

    // Thanh điều hướng dưới đáy
    private SessionStore sessionStore;
    private UserViewModel userViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sessionStore = FintrackApplication.container(this).sessionStore();
        userViewModel = new ViewModelProvider(this, FintrackApplication.container(this).viewModelFactory()).get(UserViewModel.class);

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
            userViewModel.fetchSettings();
        }
    }

    private void initViews() {
        layoutProfile = binding.layoutProfile;
        tvAvatarInitials = binding.tvAvatarInitials;
        tvProfileName = binding.tvProfileName;
        tvProfileEmail = binding.tvProfileEmail;
        btnEditProfile = binding.btnEditProfile;

        rowNotification = binding.rowNotification;
        rowWallets = binding.rowWallets;
        rowCategories = binding.rowCategories;
        rowSecurity = binding.rowSecurity;
        rowAboutApp = binding.rowAboutApp;

    }

    private void loadUserData() {
        SessionSnapshot session = sessionStore.current();
        String name = session.name;
        String email = session.email;

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
        userViewModel.getSettingsResult().observe(this, resource -> {
            if (resource != null && resource.getStatus() == Resource.Status.SUCCESS
                    && resource.getData() != null && resource.getData().getProfile() != null) {
                String name = resource.getData().getProfile().getDisplayName();
                String email = resource.getData().getProfile().getEmail();
                if (name != null) {
                    tvProfileName.setText(name);
                    tvAvatarInitials.setText(getInitials(name));
                }
                if (email != null) tvProfileEmail.setText(email);
                // UserViewModel đã lưu hồ sơ này vào Session.
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
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.about_dialog_title)
                        .setMessage(R.string.about_dialog_message)
                        .setPositiveButton(R.string.about_dialog_close, (dialog, which) -> dialog.dismiss())
                        .show();
            });
        }

        // Bottom Navigation & Speed Dial FAB
        com.example.fintrack.app.finance.ui.FinanceUi.navigation(this, R.id.navSetting);
    }
}
