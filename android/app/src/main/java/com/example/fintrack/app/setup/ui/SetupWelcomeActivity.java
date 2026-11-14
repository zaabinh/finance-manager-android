package com.example.fintrack.app.setup.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fintrack.R;
import com.example.fintrack.databinding.ActivitySetupWelcomeBinding;

// Màn hình Chào mừng (SetupWelcomeActivity) - Màn hình khởi đầu của luồng hướng dẫn thiết lập
public class SetupWelcomeActivity extends AppCompatActivity {

    private ActivitySetupWelcomeBinding binding;

    // Khai báo thành phần giao diện (UI)
    private Button btnStart; // Nút "Bắt đầu" để tiến hành cài đặt

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySetupWelcomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot()); // Gắn giao diện XML vào Activity

        initViews();       // Ánh xạ các thành phần giao diện
        setupListeners();  // Gán sự kiện bấm cho nút bấm
    }

    // Ánh xạ ID từ file XML sang biến Java
    private void initViews() {
        btnStart = binding.btnStart;
    }

    // Thiết lập xử lý sự kiện bấm nút
    private void setupListeners() {
        // Sự kiện khi bấm nút "Bắt đầu": Chuyển sang màn hình nhập thông tin cơ bản
        btnStart.setOnClickListener(v -> {
            Intent intent = new Intent(SetupWelcomeActivity.this, SetupBasicInfoActivity.class);
            startActivity(intent);
        });
    }
}
