package com.example.fintrack.app.ocr.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.fintrack.R;
import com.example.fintrack.databinding.ActivityOcrCameraBinding;
import com.example.fintrack.app.transaction.ui.TransactionReviewActivity;

import java.util.ArrayList;

/**
 * Màn hình chụp / chọn ảnh hóa đơn OCR.
 * Lấy ảnh thực tế từ Camera hoặc Gallery và truyền URI thật sang TransactionReviewActivity để trích xuất dữ liệu.
 */
public class OcrCameraActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 101;

    private ActivityOcrCameraBinding binding;

    private ImageView btnBack;
    private ImageView btnGalleryPick;
    private FrameLayout btnShutterCapture;

    // Gallery Image Picker Launcher
    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    launchReviewWithUri(uri);
                } else {
                    Toast.makeText(this, R.string.ocr_pick_cancelled, Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOcrCameraBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initViews();
        checkCameraPermission();
        setupListeners();
    }

    private void initViews() {
        btnBack = binding.btnBack;
        btnGalleryPick = binding.btnGalleryPick;
        btnShutterCapture = binding.btnShutterCapture;
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
                Toast.makeText(this, R.string.ocr_camera_granted, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.ocr_camera_denied_use_gallery, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        // Chọn ảnh thực tế từ thư viện thiết bị
        btnGalleryPick.setOnClickListener(v -> galleryLauncher.launch("image/*"));

        // Bấm nút chụp: Mở bộ chọn ảnh hoặc sử dụng ảnh mẫu nếu chạy trên Emulator không có Camera vật lý
        btnShutterCapture.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                galleryLauncher.launch("image/*");
            } else {
                Toast.makeText(this, R.string.ocr_camera_permission_needed, Toast.LENGTH_SHORT).show();
                galleryLauncher.launch("image/*");
            }
        });
    }

    private void launchReviewWithUri(Uri uri) {
        ArrayList<String> images = new ArrayList<>();
        images.add(uri.toString());
        Intent intent = new Intent(this, TransactionReviewActivity.class);
        intent.putStringArrayListExtra(TransactionReviewActivity.EXTRA_IMAGES, images);
        startActivity(intent);
        finish();
    }
}
