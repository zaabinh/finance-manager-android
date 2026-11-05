package com.example.fintrack.app.notification.model;

import java.io.Serializable;

public class NotificationItem implements Serializable {
    private String id;
    private String title;
    private String time;
    private String amount; // optional, e.g. "+300.000 đ"
    private String section; // "Hôm nay", "Hôm qua"
    private int iconResId;
    private boolean isRead;

    public NotificationItem() {}

    public NotificationItem(String id, String title, String time, String amount, String section, int iconResId, boolean isRead) {
        this.id = id;
        this.title = title;
        this.time = time;
        this.amount = amount;
        this.section = section;
        this.iconResId = iconResId;
        this.isRead = isRead;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public int getIconResId() {
        return iconResId;
    }

    public void setIconResId(int iconResId) {
        this.iconResId = iconResId;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }
}
