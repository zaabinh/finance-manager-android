package com.fintrack.api.dto.response.user;

import java.time.LocalTime;
import java.time.Instant;

public record NotificationSettingsResponse(
    Boolean notificationsEnabled,
    Boolean dailyReminderEnabled,
    LocalTime dailyReminderTime,
    Boolean budgetAlertEnabled,
    Short budgetAlertThresholdPercent,
    Boolean budgetExceedAlertEnabled,
    Boolean otherNotificationsEnabled,
    Instant updatedAt
) {

}
