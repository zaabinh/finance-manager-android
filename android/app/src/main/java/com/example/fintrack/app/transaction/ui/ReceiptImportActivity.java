package com.example.fintrack.app.transaction.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import java.io.File;
import java.util.ArrayList;

public final class ReceiptImportActivity extends FinanceActivity {
    private Uri cameraUri;
    private final ActivityResultLauncher<String[]> gallery = registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), images -> {
        ArrayList<String> batch = new ArrayList<>();
        for (Uri uri : images) {
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (SecurityException ignored) { }
            batch.add(uri.toString());
        }
        if (!batch.isEmpty()) review(batch);
    });
    private final ActivityResultLauncher<Uri> camera = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
        if (success && cameraUri != null) { ArrayList<String> batch = new ArrayList<>(); batch.add(cameraUri.toString()); review(batch); }
    });
    @Override protected int layout() { return R.layout.activity_receipt_import; }
    @Override protected void bind(Bundle saved) {
        toolbar(R.id.toolbarReceiptImport);
        if (saved != null && saved.getString("camera") != null) cameraUri = Uri.parse(saved.getString("camera"));
        findViewById(R.id.optionLibrary).setOnClickListener(view -> gallery.launch(new String[]{"image/*"}));
        findViewById(R.id.optionCamera).setOnClickListener(view -> {
            try {
                File folder = new File(getCacheDir(), "receipts");
                if (!folder.isDirectory() && !folder.mkdirs()) throw new IllegalStateException();
                File file = File.createTempFile("receipt-", ".jpg", folder);
                cameraUri = FileProvider.getUriForFile(this, getPackageName() + ".receipts", file);
                camera.launch(cameraUri);
            } catch (Exception error) { Toast.makeText(this, R.string.finance_camera_error, Toast.LENGTH_LONG).show(); }
        });
    }
    private void review(ArrayList<String> images) { startActivity(new Intent(this, ReceiptReviewActivity.class).putStringArrayListExtra("images", images)); }
    @Override protected void render() { }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); if (cameraUri != null) out.putString("camera", cameraUri.toString()); }
}
