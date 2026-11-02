package com.fintrack.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;

import java.time.Instant;
import java.util.UUID;

public class UserPreference {
    @Id
    @Column(name = "user_id")
    UUID id;

    @Column(name = "display_name", length = 100)
    String displayName;

    @Column(name = "currency_code", length = 3)
    String currencyCode = "VND";

    @Column(name = "time_zone")
    String timezone = "Asia/Ho_Chi_Minh";

    @Column(name = "notification_enabled")
    Boolean notificationEnabled = false;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt = Instant.now();

}
