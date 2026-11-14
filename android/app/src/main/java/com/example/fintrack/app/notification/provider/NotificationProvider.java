package com.example.fintrack.app.notification.provider;

import com.example.fintrack.app.notification.model.NotificationItem;

import java.util.List;

/**
 * The current account's notifications and their read state. Obtain it from AppContainer.notificationProvider();
 * null there means "not connected", which is not the same as an empty list.
 */
public interface NotificationProvider {
    /** A fresh list for the current account, each item carrying its read state. */
    List<NotificationItem> notifications();

    void markRead(String notificationId);

    void markAllRead();
}
