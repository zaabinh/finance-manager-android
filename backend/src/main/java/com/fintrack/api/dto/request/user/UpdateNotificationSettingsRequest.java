package com.fintrack.api.dto.request.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalTime;

public record UpdateNotificationSettingsRequest(
        Boolean notificationsEnabled,

        Boolean dailyReminderEnabled,

        LocalTime dailyReminderTime,

        Boolean budgetAlertEnabled,

        @Min(value = 1, message = "BUDGET_THRESHOLD_VALUE_INVALID")
        @Max(value = 100, message = "BUDGET_THRESHOLD_VALUE_INVALID")
        Short budgetAlertThresholdPercent,

        Boolean budgetExceedAlertEnabled,

        Boolean otherNotificationsEnabled
) {}
