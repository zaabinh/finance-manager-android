package com.example.fintrack.app.setup.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fintrack.R;

// Màn hình nhập thông tin cơ bản (Tên người dùng) trong luồng cài đặt
public class SetupBasicInfoActivity extends AppCompatActivity {

    // Khóa (Key) để đóng gói và gửi tên người dùng sang màn hình tiếp theo
    public static final String EXTRA_DISPLAY_NAME = "extra_display_name";

    // Khai báo các thành phần giao diện (UI)
    private ImageView ivBack;       // Nút quay lại (mũi tên back)
    private EditText etDisplayName;  // Ô nhập tên hiển thị
    private Button btnContinue;     // Nút "Tiếp tục"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_basic_info); // Gắn giao diện XML vào Activity

        initViews();       // Ánh xạ các thành phần giao diện
        setupListeners();  // Gán sự kiện bấm nút
    }

    // Ánh xạ ID từ file XML sang biến trong Java
    private void initViews() {
        ivBack = findViewById(R.id.ivBack);
        etDisplayName = findViewById(R.id.etDisplayName);
        btnContinue = findViewById(R.id.btnContinue);
    }

    // Xử lý các sự kiện bấm nút
    private void setupListeners() {
        // Nút Back: Bấm vào thì đóng màn hình hiện tại (trở về màn hình trước)
        ivBack.setOnClickListener(v -> finish());

        // Nút Tiếp tục: Xử lý thông tin tên và chuyển sang bước tiếp theo
        btnContinue.setOnClickListener(v -> {
            // Lấy chuỗi nhập từ ô EditText và cắt bỏ khoảng trắng thừa ở 2 đầu
            String name = etDisplayName.getText().toString().trim();

            // Nếu người dùng không nhập gì, lấy tên mặc định từ file resource (strings.xml)
            if (TextUtils.isEmpty(name)) {
                name = getString(R.string.setup_display_name_default);
            }

            // Tạo Intent chuyển hướng sang màn hình thiết lập thông báo (SetupNotificationActivity)
            Intent intent = new Intent(SetupBasicInfoActivity.this, SetupNotificationActivity.class);

            // Đính kèm dữ liệu tên người dùng vừa lấy được vào Intent
            intent.putExtra(EXTRA_DISPLAY_NAME, name);

            // Mở màn hình tiếp theo
            startActivity(intent);
        });
    }
}