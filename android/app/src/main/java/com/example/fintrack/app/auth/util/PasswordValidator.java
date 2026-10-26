package com.example.fintrack.app.auth.util;

import android.content.Context;
import android.text.TextUtils;

import com.example.fintrack.R;

import java.util.regex.Pattern;

public class PasswordValidator {

    private static final int MIN_LENGTH = 8;
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]");

    public static String validatePassword(Context context, String password) {
        if (TextUtils.isEmpty(password)) {
            return context.getString(R.string.error_required_field);
        }
        if (password.length() < MIN_LENGTH) {
            return context.getString(R.string.error_password_min_length);
        }
        if (!UPPERCASE_PATTERN.matcher(password).find()) {
            return context.getString(R.string.error_password_uppercase);
        }
        if (!LOWERCASE_PATTERN.matcher(password).find()) {
            return context.getString(R.string.error_password_lowercase);
        }
        if (!DIGIT_PATTERN.matcher(password).find()) {
            return context.getString(R.string.error_password_digit);
        }
        if (!SPECIAL_CHAR_PATTERN.matcher(password).find()) {
            return context.getString(R.string.error_password_special_char);
        }
        return null;
    }

    public static boolean isValid(String password) {
        return password != null
                && password.length() >= MIN_LENGTH
                && UPPERCASE_PATTERN.matcher(password).find()
                && LOWERCASE_PATTERN.matcher(password).find()
                && DIGIT_PATTERN.matcher(password).find()
                && SPECIAL_CHAR_PATTERN.matcher(password).find();
    }
}
