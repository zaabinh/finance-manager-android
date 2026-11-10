package com.example.fintrack.app.core.ui;

import android.graphics.Color;
import android.view.View;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/** Shows Sheet/* content (which draws its own bg_sheet) in a BottomSheetDialog above the keyboard. */
public final class SheetDialog {
    private SheetDialog() { }

    /** The dialog is dismissed when the host is destroyed (rotation): sheets reopen empty rather than leak. */
    public static BottomSheetDialog show(AppCompatActivity host, View content) {
        BottomSheetDialog dialog = new BottomSheetDialog(host);
        dialog.setContentView(content);
        if (dialog.getWindow() != null)
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.setOnShowListener(d -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet == null) return;
            sheet.setBackgroundColor(Color.TRANSPARENT);
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });
        LifecycleEventObserver observer = (owner, event) -> { if (event == Lifecycle.Event.ON_DESTROY) dialog.dismiss(); };
        host.getLifecycle().addObserver(observer);
        dialog.setOnDismissListener(d -> host.getLifecycle().removeObserver(observer));
        dialog.show();
        return dialog;
    }
}
