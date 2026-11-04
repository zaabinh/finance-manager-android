package com.fintrack.api.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import java.time.Instant;
import java.time.LocalTime;

import org.hibernate.annotations.UpdateTimestamp;
@Entity 
@Table(name = "notificaition_settings", schema = "fintrack")
@Getter
@Setter
@NoArgsConstructor 
@AllArgsConstructor 
@Builder 
public class NotificationSettings {
    @Id 
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId 
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "daily_reminder_enabled", nullable = false)
    @Builder.Default
    private Boolean dailyReminderEnabled = false;

    @Column(name = "daily_reminder_time")
    @Builder.Default
    private LocalTime dailyReminderTime = LocalTime.of(20, 0);

    @Column(name = "budget_alert_enabled", nullable = false)
    @Bulder.Default
    private Boolean budgetAlertEnabled = true;

    @Column(name = "budget_alert_threshold_percent", nullable = false)
    @Builder.Default
    private Short budgetAlertThresholdPercent = 80;

    @UpdateTimestamp 
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
