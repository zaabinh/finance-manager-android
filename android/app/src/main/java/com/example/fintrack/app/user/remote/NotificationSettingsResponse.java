package com.example.fintrack.app.user.remote;

import com.google.gson.annotations.SerializedName;

public class NotificationSettingsResponse {
    @SerializedName("notificationsEnabled") private boolean notificationsEnabled;
    @SerializedName("dailyReminderEnabled") private boolean dailyReminderEnabled;
    @SerializedName("dailyReminderTime") private String dailyReminderTime;
    @SerializedName("budgetAlertEnabled") private boolean budgetAlertEnabled;
    @SerializedName("budgetAlertThresholdPercent") private int budgetAlertThresholdPercent;
    @SerializedName("budgetExceedAlertEnabled") private boolean budgetExceedAlertEnabled;
    @SerializedName("otherNotificationsEnabled") private boolean otherNotificationsEnabled;

    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public boolean isDailyReminderEnabled() { return dailyReminderEnabled; }
    public String getDailyReminderTime() { return dailyReminderTime; }
    public boolean isBudgetAlertEnabled() { return budgetAlertEnabled; }
    public int getBudgetAlertThresholdPercent() { return budgetAlertThresholdPercent; }
    public boolean isBudgetExceedAlertEnabled() { return budgetExceedAlertEnabled; }
    public boolean isOtherNotificationsEnabled() { return otherNotificationsEnabled; }
}
