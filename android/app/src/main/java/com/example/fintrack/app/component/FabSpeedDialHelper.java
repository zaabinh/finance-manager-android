package com.example.fintrack.app.component;

import android.view.View;

import com.example.fintrack.R;

public class FabSpeedDialHelper {

    public interface OnSpeedDialActionListener {
        void onManualInputClick();
        void onOcrScanClick();
    }

    private final View speedDialRoot;
    private final View speedDialScrim;
    private final View btnManualInput;
    private final View btnOcrScan;
    private final View btnFabClose;
    private final OnSpeedDialActionListener listener;

    public FabSpeedDialHelper(View rootContainer, OnSpeedDialActionListener listener) {
        this.speedDialRoot = rootContainer.findViewById(R.id.speedDialRoot);
        this.speedDialScrim = rootContainer.findViewById(R.id.speedDialScim);
        this.btnManualInput = rootContainer.findViewById(R.id.btnManualInput);
        this.btnOcrScan = rootContainer.findViewById(R.id.btnOcrScan);
        this.btnFabClose = rootContainer.findViewById(R.id.btnFabClose);
        this.listener = listener;

        setupListeners();
    }

    private void setupListeners() {
        if (speedDialScrim != null) {
            speedDialScrim.setOnClickListener(v -> hideSpeedDial());
        }

        if (btnFabClose != null) {
            btnFabClose.setOnClickListener(v -> hideSpeedDial());
        }

        if (btnManualInput != null) {
            btnManualInput.setOnClickListener(v -> {
                hideSpeedDial();
                if (listener != null) {
                    listener.onManualInputClick();
                }
            });
        }

        if (btnOcrScan != null) {
            btnOcrScan.setOnClickListener(v -> {
                hideSpeedDial();
                if (listener != null) {
                    listener.onOcrScanClick();
                }
            });
        }
    }

    public void showSpeedDial() {
        if (speedDialRoot != null) {
            speedDialRoot.setVisibility(View.VISIBLE);
        }
    }

    public void hideSpeedDial() {
        if (speedDialRoot != null) {
            speedDialRoot.setVisibility(View.GONE);
        }
    }

    public boolean isSpeedDialShowing() {
        return speedDialRoot != null && speedDialRoot.getVisibility() == View.VISIBLE;
    }
}
