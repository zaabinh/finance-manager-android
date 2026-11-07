package com.example.fintrack.app.user.remote;

import com.google.gson.annotations.SerializedName;

public class UpdateNotificationSettingsRequest {
    @SerializedName("notificationsEnabled") private final boolean notificationsEnabled;
    @SerializedName("dailyReminderEnabled") private final boolean dailyReminderEnabled;
    @SerializedName("dailyReminderTime") private final String dailyReminderTime;
    @SerializedName("budgetAlertEnabled") private final boolean budgetAlertEnabled;
    @SerializedName("budgetAlertThresholdPercent") private final int budgetAlertThresholdPercent;
    @SerializedName("budgetExceedAlertEnabled") private final boolean budgetExceedAlertEnabled;
    @SerializedName("otherNotificationsEnabled") private final boolean otherNotificationsEnabled;

    public UpdateNotificationSettingsRequest(boolean notificationsEnabled, boolean dailyReminderEnabled,
            String dailyReminderTime, boolean budgetAlertEnabled, int budgetAlertThresholdPercent,
            boolean budgetExceedAlertEnabled, boolean otherNotificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
        this.dailyReminderEnabled = dailyReminderEnabled;
        this.dailyReminderTime = dailyReminderTime;
        this.budgetAlertEnabled = budgetAlertEnabled;
        this.budgetAlertThresholdPercent = budgetAlertThresholdPercent;
        this.budgetExceedAlertEnabled = budgetExceedAlertEnabled;
        this.otherNotificationsEnabled = otherNotificationsEnabled;
    }
}
