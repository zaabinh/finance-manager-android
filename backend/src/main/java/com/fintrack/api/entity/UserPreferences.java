package com.fintrack.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UpdateTimestamp;

@Entity 
@Table(name = "user_preference", schema = "fintrack")
@Getter 
@Setter 
@NoArgsConstructor 
@AllArgsConstructor 
@Builder 
public class UserPreferences {
    @Id
    @Column(name = "user_id", nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "currency_code", length = 3, nullable = false, columnDefinition = "bpchar(3)")
    @Builder.Default
    private String currencyCode = "VND";

    @Column(name = "time_zone", nullable = false)
    @Builder.Default
    String timezone = "Asia/Ho_Chi_Minh";

    @Column(name = "week_starts_on", nullable = false)
    @Builder.Default
    private Short weekStartsOn = 1;

    @Column(name = "notification_enabled", nullable = false)
    @Builder.Default
    Boolean notificationEnabled = false;

    @UpdateTimestamp 
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    Instant updatedAt = Instant.now();

}
