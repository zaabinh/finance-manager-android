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
    private TextView tvProfileName;
    private TextView tvProfileEmail;
    private ImageButton btnEditProfile;

    private TextView rowNotification;
    private TextView rowWallets;
    private TextView rowCategories;
    private TextView rowSecurity;

    // Thanh điều hướng dưới đáy
    private ImageView navHome;
    private SessionManager sessionManager;
    private UserViewModel userViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting); // Gắn file XML tương ứng

        sessionManager = SessionManager.getInstance(getApplicationContext());
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);

        initViews();       // Ánh xạ ID từ XML
        loadUserData();    // Load dữ liệu ban đầu
        setupObservers();  // Quan sát ViewModel
        setupListeners();  // Gán sự kiện click cho các nút
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserData();    // Cập nhật lại thông tin user mỗi khi quay lại màn hình Setting
        if (userViewModel != null) {
            userViewModel.fetchProfile();
        }
    }

    // Ánh xạ các thành phần giao diện
    private void initViews() {
        layoutProfile = findViewById(R.id.layoutProfile);
        tvProfileName = findViewById(R.id.tvProfileName);
        tvProfileEmail = findViewById(R.id.tvProfileEmail);
        btnEditProfile = findViewById(R.id.btnEditProfile);

        rowNotification = findViewById(R.id.rowNotification);
        rowWallets = findViewById(R.id.rowWallets);
        rowCategories = findViewById(R.id.rowCategories);
        rowSecurity = findViewById(R.id.rowSecurity);

        navHome = findViewById(R.id.navHome);
    }

    // Tải thông tin người dùng từ SessionManager lên giao diện
    private void loadUserData() {
        String name = sessionManager.getUserName();
        String email = sessionManager.getUserEmail();

        if (name != null && !name.isEmpty()) {
            tvProfileName.setText(name);
        }
        if (email != null && !email.isEmpty()) {
            tvProfileEmail.setText(email);
        }
    }

    // Quan sát UserViewModel để cập nhật profile từ server/mock
    private void setupObservers() {
        userViewModel.getProfileResult().observe(this, resource -> {
            if (resource != null && resource.getStatus() == Resource.Status.SUCCESS && resource.getData() != null) {
                String name = resource.getData().getName();
                String email = resource.getData().getEmail();
                if (name != null) tvProfileName.setText(name);
                if (email != null) tvProfileEmail.setText(email);
                sessionManager.saveUserProfile(resource.getData().getUserId(), email, name);
            }
        });
    }

    // Thiết lập sự kiện tương tác
    private void setupListeners() {
        // 1. Nhấn vào thẻ hồ sơ hoặc nút chỉnh sửa -> Mở màn hình SettingUserActivity
        View.OnClickListener openProfileListener = v -> {
            Intent intent = new Intent(SettingActivity.this, SettingUserActivity.class);
            startActivity(intent);
        };

        layoutProfile.setOnClickListener(openProfileListener);
        btnEditProfile.setOnClickListener(openProfileListener);

        // 2. Cấu hình thông báo -> Mở SettingNotificationActivity
        rowNotification.setOnClickListener(v -> {
            Intent intent = new Intent(SettingActivity.this, SettingNotificationActivity.class);
            startActivity(intent);
        });

        // 3. Quản lý Ví -> Mở WalletListActivity
        rowWallets.setOnClickListener(v -> {
            Intent intent = new Intent(SettingActivity.this, com.example.fintrack.app.wallet.ui.WalletListActivity.class);
            startActivity(intent);
        });

        // 4. Quản lý Danh mục -> Mở CategoryListActivity
        rowCategories.setOnClickListener(v -> {
            Intent intent = new Intent(SettingActivity.this, com.example.fintrack.app.category.ui.CategoryListActivity.class);
            startActivity(intent);
        });

        // 5. Bảo mật & Tài khoản -> Mở SettingSecurityActivity
        rowSecurity.setOnClickListener(v -> {
            Intent intent = new Intent(SettingActivity.this, SettingSecurityActivity.class);
            startActivity(intent);
        });

        // 6. Chuyển về màn hình Trang chủ khi bấm icon Home trên thanh điều hướng dưới
        if (navHome != null) {
            navHome.setOnClickListener(v -> {
                Intent intent = new Intent(SettingActivity.this, HomeDashboardActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            });
        }
    }
}
