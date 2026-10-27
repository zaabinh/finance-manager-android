package com.example.fintrack.app.auth.util;

import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.widget.EditText;
import android.widget.ImageView;

public class PasswordToggleHelper {

    public static boolean togglePasswordVisibility(EditText editText, ImageView toggleView, boolean isCurrentlyVisible) {
        if (editText == null || toggleView == null) return false;

        boolean newVisibility = !isCurrentlyVisible;
        if (newVisibility) {
            editText.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            toggleView.setAlpha(1.0f);
        } else {
            editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            toggleView.setAlpha(0.5f);
        }
        editText.setSelection(editText.getText().length());
        return newVisibility;
    }
}
