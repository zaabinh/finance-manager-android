package com.example.fintrack.app.ocr.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.fintrack.R;

public class OcrReviewActivity extends AppCompatActivity {

    private ImageView btnBack;
    private Button btnConfirmImport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ocr_review);

        initViews();
        setupListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnConfirmImport = findViewById(R.id.btnConfirmImport);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnConfirmImport.setOnClickListener(v -> {
            Toast.makeText(this, "Đã nhập thành công 3 giao dịch mới!", Toast.LENGTH_LONG).show();
            finish();
        });
    }
}
