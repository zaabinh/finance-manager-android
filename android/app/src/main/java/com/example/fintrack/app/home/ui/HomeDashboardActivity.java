package com.example.fintrack.app.home.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.fintrack.R;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.SessionManager;
import com.example.fintrack.app.settings.ui.SettingActivity;
import com.example.fintrack.app.user.viewmodel.UserViewModel;

// Màn hình chính của ứng dụng (HomeDashboardActivity)
public class HomeDashboardActivity extends AppCompatActivity {

    private ImageView navSetting;
    private TextView tvUserName;
    private SessionManager sessionManager;
    private UserViewModel userViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_dashboard);

        sessionManager = SessionManager.getInstance(getApplicationContext());
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);

        navSetting = findViewById(R.id.navSetting);
        tvUserName = findViewById(R.id.tvUserName);
        TextView tvSeeMoreWallets = findViewById(R.id.tvSeeMoreWallets);

        tvSeeMoreWallets.setOnClickListener(v -> {
            Intent intent = new Intent(HomeDashboardActivity.this, com.example.fintrack.app.wallet.ui.WalletListActivity.class);
            startActivity(intent);
        });

        // Hiển thị tên từ SessionManager trước
        String name = sessionManager.getUserName();
        if (name != null && !name.isEmpty()) {
            tvUserName.setText(name);
        }

        // Quan sát dữ liệu profile từ API/Mock để đồng bộ mới nhất
        userViewModel.getProfileResult().observe(this, resource -> {
            if (resource != null && resource.getStatus() == Resource.Status.SUCCESS && resource.getData() != null) {
                String fetchedName = resource.getData().getName();
                if (fetchedName != null && !fetchedName.isEmpty()) {
                    tvUserName.setText(fetchedName);
                    sessionManager.saveUserProfile(
                            resource.getData().getUserId(),
                            resource.getData().getEmail(),
                            fetchedName
                    );
                }
            }
        });

        // Tải profile khi mở màn hình
        userViewModel.fetchProfile();

        navSetting.setOnClickListener(v -> {
            Intent intent = new Intent(
                    HomeDashboardActivity.this,
                    SettingActivity.class
            );
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (userViewModel != null) {
            userViewModel.fetchProfile();
        }
    }
}
