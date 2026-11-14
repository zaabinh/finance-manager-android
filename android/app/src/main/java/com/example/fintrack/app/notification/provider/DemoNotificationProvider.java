package com.example.fintrack.app.notification.provider;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.fintrack.R;
import com.example.fintrack.app.notification.model.NotificationItem;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Demo notifications for the mock flavor only (AppContainer creates it). Read state is kept per account and per
 * notification id; the account is the session's userId, stored only as a SHA-256 hash.
 */
public final class DemoNotificationProvider implements NotificationProvider {
    /** Read notification ids per (hashed) account. */
    public interface ReadStore {
        Set<String> readIds(String accountKey);
        void saveReadIds(String accountKey, Set<String> ids);
    }

    private final Supplier<String> userId;
    private final ReadStore store;

    public DemoNotificationProvider(Supplier<String> userId, ReadStore store) {
        this.userId = userId;
        this.store = store;
    }

    @Override
    public List<NotificationItem> notifications() {
        String account = accountKey();
        Set<String> read = account == null ? Collections.emptySet() : store.readIds(account);
        List<NotificationItem> items = new ArrayList<>();
        items.add(new NotificationItem("n1", "Vượt hạn mức Mua sắm", "18:31", "+300.000 đ", "Hôm nay", R.drawable.ic_receipt, read.contains("n1")));
        items.add(new NotificationItem("n2", "Hóa đơn & tiện ích đã dùng 88%", "07:31", null, "Hôm nay", R.drawable.ic_receipt, read.contains("n2")));
        items.add(new NotificationItem("n3", "Nhắc ghi chi tiêu hôm nay", "21:00", null, "Hôm qua", R.drawable.ic_transactions, read.contains("n3")));
        return items;
    }

    @Override
    public void markRead(String notificationId) {
        String account = accountKey();
        if (account == null) return;
        Set<String> read = new HashSet<>(store.readIds(account));
        if (read.add(notificationId)) store.saveReadIds(account, read);
    }

    @Override
    public void markAllRead() {
        String account = accountKey();
        if (account == null) return;
        Set<String> read = new HashSet<>(store.readIds(account));
        for (NotificationItem item : notifications()) read.add(item.getId());
        store.saveReadIds(account, read);
    }

    /** Null without a signed-in account: nothing is read and nothing is saved. */
    private String accountKey() {
        String id = userId.get();
        if (id == null || id.trim().isEmpty()) return null;
        try {
            StringBuilder hex = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8)))
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Plain SharedPreferences; the preferences file is opened on first use. */
    public static ReadStore preferences(Context context) {
        return new ReadStore() {
            private SharedPreferences prefs() {
                return context.getSharedPreferences("fintrack_notification_read", Context.MODE_PRIVATE);
            }
            @Override public Set<String> readIds(String accountKey) {
                return new HashSet<>(prefs().getStringSet(accountKey, Collections.emptySet()));
            }
            @Override public void saveReadIds(String accountKey, Set<String> ids) {
                prefs().edit().putStringSet(accountKey, new HashSet<>(ids)).apply();
            }
        };
    }
}
