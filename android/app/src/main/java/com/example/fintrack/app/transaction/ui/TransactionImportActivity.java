package com.example.fintrack.app.transaction.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.view.View;
import com.example.fintrack.databinding.ActivityTransactionImportBinding;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import java.util.ArrayList;

/**
 * Màn hình lựa chọn phương thức nhập hóa đơn (Chụp ảnh qua Camera hoặc Chọn ảnh từ Thư viện).
 */
public final class TransactionImportActivity extends FinanceActivity {
    private static final String STATE_CAMERA = "camera";
    private Uri cameraUri;

    // Bộ khởi chạy chọn ảnh từ thư viện thiết bị
    private final ActivityResultLauncher<String[]> gallery = registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), images -> {
        ArrayList<String> batch = new ArrayList<>();
        for (Uri uri : images) {
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (SecurityException ignored) { }
            batch.add(uri.toString());
        }
        if (!batch.isEmpty()) review(batch);
    });

    private ActivityTransactionImportBinding binding;
    @Override protected View contentView() { binding = ActivityTransactionImportBinding.inflate(getLayoutInflater()); return binding.getRoot(); }

    @Override protected void bind(Bundle saved) {
        binding.toolbarTransactionImport.setNavigationOnClickListener(view -> finish());
        if (saved != null && saved.getString(STATE_CAMERA) != null) cameraUri = Uri.parse(saved.getString(STATE_CAMERA));

        // Nút chọn ảnh từ thư viện
        binding.optionLibrary.setOnClickListener(view -> gallery.launch(new String[]{"image/*"}));

        // Nút mở camera chụp ảnh hóa đơn
        binding.optionCamera.setOnClickListener(view -> startActivity(new Intent(this, com.example.fintrack.app.ocr.ui.OcrCameraActivity.class)));
    }

    // Chuyển sang màn hình xem lại kết quả quét hóa đơn
    private void review(ArrayList<String> images) {
        startActivity(new Intent(this, TransactionReviewActivity.class).putStringArrayListExtra(TransactionReviewActivity.EXTRA_IMAGES, images));
    }

    @Override protected void render() { }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (cameraUri != null) out.putString(STATE_CAMERA, cameraUri.toString());
    }
}
