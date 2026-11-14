package com.example.fintrack.app.ocr.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.example.fintrack.R;
import com.example.fintrack.app.transaction.ui.TransactionReviewActivity;
import com.example.fintrack.databinding.ActivityOcrCameraBinding;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

/**
 * Màn hình chụp hóa đơn OCR bằng CameraX trực tiếp (In-App Live Viewfinder).
 * Cung cấp khung hình căn chỉnh thời gian thực, bật/tắt đèn Flash, chụp nhanh và chuyển sang xem lại.
 */
public class OcrCameraActivity extends AppCompatActivity {

    private ActivityOcrCameraBinding binding;
    private ImageCapture imageCapture;
    private Camera camera;
    private boolean isTorchOn = false;
    private boolean isCapturing = false;

    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    binding.layoutPermissionDenied.setVisibility(View.GONE);
                    startCamera();
                } else {
                    binding.layoutPermissionDenied.setVisibility(View.VISIBLE);
                    Toast.makeText(this, R.string.ocr_camera_denied_use_gallery, Toast.LENGTH_LONG).show();
                }
            }
    );

    private final ActivityResultLauncher<String[]> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(),
            uris -> {
                if (uris != null && !uris.isEmpty()) {
                    ArrayList<String> images = new ArrayList<>();
                    for (Uri uri : uris) {
                        try {
                            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException ignored) {}
                        images.add(uri.toString());
                    }
                    if (!images.isEmpty()) {
                        launchReviewWithBatch(images);
                        return;
                    }
                }
                Toast.makeText(this, R.string.ocr_pick_cancelled, Toast.LENGTH_SHORT).show();
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityOcrCameraBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupListeners();
        checkPermissionAndStartCamera();
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnGalleryPick.setOnClickListener(v -> galleryLauncher.launch(new String[]{"image/*"}));
        binding.btnRequestPermission.setOnClickListener(v ->
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA));

        binding.btnShutterCapture.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                takePhoto();
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            }
        });

        binding.btnFlash.setOnClickListener(v -> toggleTorch());
    }

    private void checkPermissionAndStartCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            binding.layoutPermissionDenied.setVisibility(View.GONE);
            startCamera();
        } else {
            binding.layoutPermissionDenied.setVisibility(View.VISIBLE);
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.viewFinder.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageCapture
                );

            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, R.string.ocr_camera_denied_use_gallery, Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void toggleTorch() {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            isTorchOn = !isTorchOn;
            camera.getCameraControl().enableTorch(isTorchOn);
            binding.btnFlash.setImageResource(isTorchOn ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
        } else {
            Toast.makeText(this, "Thiết bị không hỗ trợ đèn flash", Toast.LENGTH_SHORT).show();
        }
    }

    private void takePhoto() {
        if (imageCapture == null || isCapturing) return;

        isCapturing = true;
        binding.ivShutterIcon.setVisibility(View.GONE);
        binding.progressBarCapture.setVisibility(View.VISIBLE);

        File receiptsDir = new File(getCacheDir(), "receipts");
        if (!receiptsDir.exists()) {
            receiptsDir.mkdirs();
        }

        File photoFile = new File(receiptsDir, "receipt_" + System.currentTimeMillis() + ".jpg");

        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        isCapturing = false;
                        binding.ivShutterIcon.setVisibility(View.VISIBLE);
                        binding.progressBarCapture.setVisibility(View.GONE);

                        Uri savedUri = FileProvider.getUriForFile(
                                OcrCameraActivity.this,
                                getPackageName() + ".receipts",
                                photoFile
                        );
                        launchReviewWithUri(savedUri);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        isCapturing = false;
                        binding.ivShutterIcon.setVisibility(View.VISIBLE);
                        binding.progressBarCapture.setVisibility(View.GONE);
                        Toast.makeText(OcrCameraActivity.this, R.string.ocr_camera_denied_use_gallery, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void launchReviewWithUri(Uri uri) {
        ArrayList<String> images = new ArrayList<>();
        images.add(uri.toString());
        launchReviewWithBatch(images);
    }

    private void launchReviewWithBatch(ArrayList<String> images) {
        Intent intent = new Intent(this, TransactionReviewActivity.class);
        intent.putStringArrayListExtra(TransactionReviewActivity.EXTRA_IMAGES, images);
        startActivity(intent);
        finish();
    }
}
