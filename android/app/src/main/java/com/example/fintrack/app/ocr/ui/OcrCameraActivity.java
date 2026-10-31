package com.example.fintrack.app.ocr.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.fintrack.R;

public class OcrCameraActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 101;

    private ImageView btnBack;
    private ImageView btnGalleryPick;
    private FrameLayout btnShutterCapture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ocr_camera);

        initViews();
        checkCameraPermission();
        setupListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnGalleryPick = findViewById(R.id.btnGalleryPick);
        btnShutterCapture = findViewById(R.id.btnShutterCapture);
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Đã cấp quyền Camera", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Cần quyền Camera để quét hóa đơn", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnGalleryPick.setOnClickListener(v -> {
            Intent intent = new Intent(this, OcrReviewActivity.class);
            startActivity(intent);
            finish();
        });

        btnShutterCapture.setOnClickListener(v -> {
            Toast.makeText(this, "Đang trích xuất dữ liệu hóa đơn...", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, OcrReviewActivity.class);
            startActivity(intent);
            finish();
        });
    }
}
