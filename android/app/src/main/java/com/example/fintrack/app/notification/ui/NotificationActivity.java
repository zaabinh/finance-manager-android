package com.example.fintrack.app.notification.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fintrack.R;
import com.example.fintrack.app.notification.adapter.NotificationAdapter;
import com.example.fintrack.app.notification.model.NotificationItem;
import com.example.fintrack.app.settings.ui.SettingNotificationActivity;

import java.util.ArrayList;
import java.util.List;

public class NotificationActivity extends AppCompatActivity {

    private static final String PREF_NOTIF = "fintrack_notif_prefs";
    private static final String KEY_ALL_READ = "all_notifications_read";

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);

        initViews();
        setupRecyclerViews();
        loadNotifications();
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
            todayAdapter.notifyDataSetChanged();
            Toast.makeText(this, "Đã chọn: " + item.getTitle(), Toast.LENGTH_SHORT).show();
        });
        rvTodayNotifs.setLayoutManager(new LinearLayoutManager(this));
        rvTodayNotifs.setAdapter(todayAdapter);

        yesterdayAdapter = new NotificationAdapter(item -> {
            item.setRead(true);
            yesterdayAdapter.notifyDataSetChanged();
            Toast.makeText(this, "Đã chọn: " + item.getTitle(), Toast.LENGTH_SHORT).show();
        });
        rvYesterdayNotifs.setLayoutManager(new LinearLayoutManager(this));
        rvYesterdayNotifs.setAdapter(yesterdayAdapter);
    }

    private void loadNotifications() {
        SharedPreferences prefs = getSharedPreferences(PREF_NOTIF, Context.MODE_PRIVATE);
        boolean isAllRead = prefs.getBoolean(KEY_ALL_READ, false);

        todayList.clear();
        todayList.add(new NotificationItem("n1", "Vượt hạn mức Mua sắm", "18:31", "+300.000 đ", "Hôm nay", R.drawable.ic_receipt, isAllRead));
        todayList.add(new NotificationItem("n2", "Hóa đơn & tiện ích đã dùng 88%", "07:31", null, "Hôm nay", R.drawable.ic_receipt, isAllRead));

        yesterdayList.clear();
        yesterdayList.add(new NotificationItem("n3", "Nhắc ghi chi tiêu hôm nay", "21:00", null, "Hôm qua", R.drawable.ic_transactions, isAllRead));

        todayAdapter.setNotifications(todayList);
        yesterdayAdapter.setNotifications(yesterdayList);

        // Màn hình trống chỉ hiển thị khi thực sự không có thông báo nào
        boolean hasNotifs = !todayList.isEmpty() || !yesterdayList.isEmpty();
        showPopulatedState(hasNotifs);
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

            // Lưu trạng thái đã đọc local
            getSharedPreferences(PREF_NOTIF, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_ALL_READ, true)
                    .apply();

            Toast.makeText(this, "Đã đánh dấu đọc tất cả thông báo", Toast.LENGTH_SHORT).show();
        });

        btnOpenSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingNotificationActivity.class);
            startActivity(intent);
        });
    }
}
