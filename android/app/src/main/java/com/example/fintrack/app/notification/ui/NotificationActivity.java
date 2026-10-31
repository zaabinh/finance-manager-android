package com.example.fintrack.app.notification.ui;

import android.content.Intent;
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

    private ImageView btnBack;
    private TextView btnMarkAllRead;
    private ScrollView layoutPopulatedState;
    private View layoutEmptyState;
    private RecyclerView rvTodayNotifs;
    private RecyclerView rvYesterdayNotifs;
    private Button btnOpenSettings;

    private NotificationAdapter todayAdapter;
    private NotificationAdapter yesterdayAdapter;

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
        todayAdapter = new NotificationAdapter(item ->
                Toast.makeText(this, "Đã chọn: " + item.getTitle(), Toast.LENGTH_SHORT).show()
        );
        rvTodayNotifs.setLayoutManager(new LinearLayoutManager(this));
        rvTodayNotifs.setAdapter(todayAdapter);

        yesterdayAdapter = new NotificationAdapter(item ->
                Toast.makeText(this, "Đã chọn: " + item.getTitle(), Toast.LENGTH_SHORT).show()
        );
        rvYesterdayNotifs.setLayoutManager(new LinearLayoutManager(this));
        rvYesterdayNotifs.setAdapter(yesterdayAdapter);
    }

    private void loadNotifications() {
        List<NotificationItem> todayList = new ArrayList<>();
        todayList.add(new NotificationItem("n1", "Vượt hạn mức Mua sắm", "18:31", "+300.000 đ", "Hôm nay", R.drawable.ic_receipt, false));
        todayList.add(new NotificationItem("n2", "Hóa đơn & tiện ích đã dùng 88%", "07:31", null, "Hôm nay", R.drawable.ic_receipt, false));

        List<NotificationItem> yesterdayList = new ArrayList<>();
        yesterdayList.add(new NotificationItem("n3", "Nhắc ghi chi tiêu hôm nay", "21:00", null, "Hôm qua", R.drawable.ic_transactions, false));

        todayAdapter.setNotifications(todayList);
        yesterdayAdapter.setNotifications(yesterdayList);

        showPopulatedState(true);
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

        btnMarkAllRead.setOnClickListener(v -> {
            todayAdapter.setNotifications(new ArrayList<>());
            yesterdayAdapter.setNotifications(new ArrayList<>());
            showPopulatedState(false);
            Toast.makeText(this, "Đã đọc hết thông báo", Toast.LENGTH_SHORT).show();
        });

        btnOpenSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingNotificationActivity.class);
            startActivity(intent);
        });
    }
}
