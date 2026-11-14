package com.example.fintrack.app.component;

import android.view.View;

import com.example.fintrack.R;

public class FabSpeedDialHelper {

    public interface OnSpeedDialActionListener {
        void onManualInputClick();
        void onOcrScanClick();
        void onVoiceInputClick();
    }

    private final View speedDialRoot;
    private final View speedDialScrim;
    private final View btnManualInput;
    private final View btnOcrScan;
    private final View btnVoiceInput;
    private final View btnFabClose;
    private final OnSpeedDialActionListener listener;

    public FabSpeedDialHelper(View rootContainer, OnSpeedDialActionListener listener) {
        this.speedDialRoot = rootContainer.findViewById(R.id.speedDialRoot);
        this.speedDialScrim = rootContainer.findViewById(R.id.speedDialScrim);
        this.btnManualInput = rootContainer.findViewById(R.id.btnManualInput);
        this.btnOcrScan = rootContainer.findViewById(R.id.btnOcrScan);
        this.btnVoiceInput = rootContainer.findViewById(R.id.btnVoiceInput);
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

        if (btnVoiceInput != null) {
            btnVoiceInput.setOnClickListener(v -> {
                hideSpeedDial();
                if (listener != null) {
                    listener.onVoiceInputClick();
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
