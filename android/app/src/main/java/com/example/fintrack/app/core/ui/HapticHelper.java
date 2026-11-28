package com.example.fintrack.app.core.ui;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/**
 * Tiện ích tạo phản hồi rung (Haptic Feedback) đa tầng cho các thao tác rảnh tay và tương tác nhanh.
 */
public final class HapticHelper {

    private HapticHelper() {}

    private static Vibrator getVibrator(Context context) {
        if (context == null) return null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager manager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                return manager != null ? manager.getDefaultVibrator() : null;
            } else {
                return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Rung nhẹ 1 nhịp ngắn (Micro bắt đầu lắng nghe).
     */
    public static void tick(Context context) {
        Vibrator v = getVibrator(context);
        if (v == null || !v.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(25);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Rung kép nhẹ 2 nhịp (Kết thúc thu âm, bắt đầu xử lý).
     */
    public static void doubleTick(Context context) {
        Vibrator v = getVibrator(context);
        if (v == null || !v.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(new long[]{0, 20, 40, 20}, -1));
            } else {
                v.vibrate(new long[]{0, 20, 40, 20}, -1);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Rung xác nhận thành công (Giao dịch đã được lưu).
     */
    public static void success(Context context) {
        Vibrator v = getVibrator(context);
        if (v == null || !v.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(new long[]{0, 35, 50, 45}, -1));
            } else {
                v.vibrate(new long[]{0, 35, 50, 45}, -1);
            }
        } catch (Exception ignored) {}
    }
}
