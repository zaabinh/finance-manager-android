package com.example.fintrack.app.notification.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.fintrack.R;
import com.example.fintrack.app.FintrackApplication;
import com.example.fintrack.app.notification.adapter.NotificationAdapter;
import com.example.fintrack.app.notification.model.NotificationItem;
import com.example.fintrack.app.notification.provider.NotificationProvider;
import com.example.fintrack.app.settings.ui.SettingNotificationActivity;
import com.example.fintrack.databinding.ActivityNotificationBinding;

import java.util.ArrayList;
import java.util.List;

public class NotificationActivity extends AppCompatActivity {

    private ActivityNotificationBinding binding;
    private NotificationAdapter todayAdapter;
    private NotificationAdapter yesterdayAdapter;
    // null = not connected (real flavors); read state always lives in the provider, never here.
    private NotificationProvider provider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNotificationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        provider = FintrackApplication.container(this).notificationProvider();
        setupRecyclerViews();
        setupListeners();
        if (provider == null) showNotConnected();
        else loadNotifications();
    }

    private void setupRecyclerViews() {
        todayAdapter = new NotificationAdapter(this::onItemClick);
        binding.rvTodayNotifs.setLayoutManager(new LinearLayoutManager(this));
        binding.rvTodayNotifs.setAdapter(todayAdapter);

        yesterdayAdapter = new NotificationAdapter(this::onItemClick);
        binding.rvYesterdayNotifs.setLayoutManager(new LinearLayoutManager(this));
        binding.rvYesterdayNotifs.setAdapter(yesterdayAdapter);
    }

    private void onItemClick(NotificationItem item) {
        provider.markRead(item.getId());
        loadNotifications();
        Toast.makeText(this, "Đã chọn: " + item.getTitle(), Toast.LENGTH_SHORT).show();
    }

    private void loadNotifications() {
        List<NotificationItem> today = new ArrayList<>();
        List<NotificationItem> yesterday = new ArrayList<>();
        String todayLabel = getString(R.string.section_today);
        for (NotificationItem item : provider.notifications())
            (todayLabel.equals(item.getSection()) ? today : yesterday).add(item);

        todayAdapter.setNotifications(today);
        yesterdayAdapter.setNotifications(yesterday);

        // Màn hình trống chỉ hiển thị khi thực sự không có thông báo nào
        boolean hasNotifs = !today.isEmpty() || !yesterday.isEmpty();
        if (!hasNotifs) {
            binding.tvEmptyTitle.setText(R.string.no_notifications_title);
            binding.tvEmptyBody.setText(R.string.no_notifications_desc);
        }
        showPopulatedState(hasNotifs);
    }

    /** Real flavors: no notification source yet. Nothing is fabricated, read actions are hidden, settings stay reachable. */
    private void showNotConnected() {
        binding.tvEmptyTitle.setText(R.string.notification_not_connected_title);
        binding.tvEmptyBody.setText(R.string.notification_not_connected_body);
        showPopulatedState(false);
    }

    private void showPopulatedState(boolean isPopulated) {
        binding.layoutPopulatedState.setVisibility(isPopulated ? View.VISIBLE : View.GONE);
        binding.layoutEmptyState.setVisibility(isPopulated ? View.GONE : View.VISIBLE);
        binding.btnMarkAllRead.setVisibility(isPopulated ? View.VISIBLE : View.GONE);
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnMarkAllRead.setOnClickListener(v -> {
            provider.markAllRead();
            loadNotifications();
            Toast.makeText(this, "Đã đánh dấu đọc tất cả thông báo", Toast.LENGTH_SHORT).show();
        });

        binding.btnOpenSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingNotificationActivity.class)));
    }
}
