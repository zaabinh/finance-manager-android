package com.example.fintrack.app.auth.util;

import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.widget.EditText;
import android.widget.ImageView;

import com.example.fintrack.R;

public class PasswordToggleHelper {

    /**
     * Toggles the password visibility in an EditText and updates the toggle ImageView icon.
     *
     * @param editText           the password EditText
     * @param toggleView         the ImageView eye toggle
     * @param isCurrentlyVisible true if password is currently visible, false if hidden
     * @return the new visibility state (true = visible, false = hidden)
     */
    public static boolean togglePasswordVisibility(EditText editText, ImageView toggleView, boolean isCurrentlyVisible) {
        if (editText == null || toggleView == null) return false;

        boolean newVisibility = !isCurrentlyVisible;
        if (newVisibility) {
            // Show password
            editText.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            toggleView.setImageResource(R.drawable.ic_eye);
            toggleView.setAlpha(1.0f);
        } else {
            // Hide password (default)
            editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            toggleView.setImageResource(R.drawable.ic_eye_close);
            toggleView.setAlpha(1.0f);
        }
        editText.setSelection(editText.getText().length());
        return newVisibility;
    }
}
