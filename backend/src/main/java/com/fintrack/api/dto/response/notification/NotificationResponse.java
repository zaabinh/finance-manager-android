package com.fintrack.api.dto.response.notification;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record NotificationResponse(UUID notificationId, String type, String title, String body,
        Instant createdAt, Instant readAt, Long amountMinor, Map<String, Object> target) {}
