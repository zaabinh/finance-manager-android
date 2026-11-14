package com.example.fintrack.app.notification.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fintrack.R;
import com.example.fintrack.app.FintrackApplication;
import com.example.fintrack.app.notification.adapter.NotificationAdapter;
import com.example.fintrack.app.notification.model.NotificationItem;
import com.example.fintrack.app.notification.provider.NotificationProvider;
import com.example.fintrack.app.settings.ui.SettingNotificationActivity;

import java.util.ArrayList;
import java.util.List;

public class NotificationActivity extends AppCompatActivity {

    private ImageView btnBack;
    private TextView btnMarkAllRead;
    private ScrollView layoutPopulatedState;
    private View layoutEmptyState;
    private RecyclerView rvTodayNotifs;
    private RecyclerView rvYesterdayNotifs;
    private Button btnOpenSettings;

    private NotificationAdapter todayAdapter;
    private NotificationAdapter yesterdayAdapter;

    private List<NotificationItem> todayList = new ArrayList<>();
    private List<NotificationItem> yesterdayList = new ArrayList<>();
    private NotificationProvider provider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);

        initViews();
        provider = FintrackApplication.container(this).notificationProvider();
        setupRecyclerViews();
        if (provider == null) showNotConnected();
        else loadNotifications();
        setupListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnMarkAllRead = findViewById(R.id.btnMarkAllRead);
        layoutPopulatedState = findViewById(R.id.layoutPopulatedState);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        rvTodayNotifs = findViewById(R.id.rvTodayNotifs);
        rvYesterdayNotifs = findViewById(R.id.rvYesterdayNotifs);
        btnOpenSettings = findViewById(R.id.btnOpenSettings);
    }

    private void setupRecyclerViews() {
        todayAdapter = new NotificationAdapter(item -> {
            item.setRead(true);
            provider.markRead(item.getId());
            todayAdapter.notifyDataSetChanged();
            Toast.makeText(this, "Đã chọn: " + item.getTitle(), Toast.LENGTH_SHORT).show();
        });
        rvTodayNotifs.setLayoutManager(new LinearLayoutManager(this));
        rvTodayNotifs.setAdapter(todayAdapter);

        yesterdayAdapter = new NotificationAdapter(item -> {
            item.setRead(true);
            provider.markRead(item.getId());
            yesterdayAdapter.notifyDataSetChanged();
            Toast.makeText(this, "Đã chọn: " + item.getTitle(), Toast.LENGTH_SHORT).show();
        });
        rvYesterdayNotifs.setLayoutManager(new LinearLayoutManager(this));
        rvYesterdayNotifs.setAdapter(yesterdayAdapter);
    }

    private void loadNotifications() {
        todayList.clear();
        yesterdayList.clear();
        String today = getString(R.string.section_today);
        for (NotificationItem item : provider.notifications())
            (today.equals(item.getSection()) ? todayList : yesterdayList).add(item);

        todayAdapter.setNotifications(todayList);
        yesterdayAdapter.setNotifications(yesterdayList);

        // Màn hình trống chỉ hiển thị khi thực sự không có thông báo nào
        boolean hasNotifs = !todayList.isEmpty() || !yesterdayList.isEmpty();
        showPopulatedState(hasNotifs);
    }

    /** Real flavors: no notification source yet. Nothing fabricated is shown; notification settings stay reachable. */
    private void showNotConnected() {
        showPopulatedState(false);
        // shortcut: the empty state's texts have no ids; the UI agent replaces this with a proper not-connected state.
        ViewGroup content = (ViewGroup) btnOpenSettings.getParent();
        ((TextView) content.getChildAt(1)).setText(R.string.notification_not_connected_title);
        ((TextView) content.getChildAt(2)).setText(R.string.notification_not_connected_body);
    }

    private void showPopulatedState(boolean isPopulated) {
        if (isPopulated) {
            layoutPopulatedState.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
            btnMarkAllRead.setVisibility(View.VISIBLE);
        } else {
            layoutPopulatedState.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
            btnMarkAllRead.setVisibility(View.GONE);
        }
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        // Đánh dấu đã đọc: giữ nguyên danh sách, chỉ đổi trạng thái đã đọc và lưu local
        btnMarkAllRead.setOnClickListener(v -> {
            for (NotificationItem item : todayList) {
                item.setRead(true);
            }
            for (NotificationItem item : yesterdayList) {
                item.setRead(true);
            }

            todayAdapter.notifyDataSetChanged();
            yesterdayAdapter.notifyDataSetChanged();

            // Lưu trạng thái đã đọc theo tài khoản
            provider.markAllRead();

            Toast.makeText(this, "Đã đánh dấu đọc tất cả thông báo", Toast.LENGTH_SHORT).show();
        });

        btnOpenSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingNotificationActivity.class);
            startActivity(intent);
        });
    }
}
