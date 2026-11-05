package com.fintrack.api.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.fintrack.api.entity.enums.UserRole;
import com.fintrack.api.entity.enums.UserStatus;

import lombok.*;

@Entity
@Table(name = "users", schema = "fintrack")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id 
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false) 
    private UUID id;

    @Column(nullable = false, columnDefinition = "citext") 
    private String username;

    @Column(nullable = false, columnDefinition = "citext") 
    private String email;
    @Column(name = "password_hash", nullable = false, columnDefinition = "text") 
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, columnDefinition = "text")
    @Builder.Default
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text") 
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "email_verified", nullable = false) 
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "onboarding_completed", nullable = false) 
    @Builder.Default
    private boolean onboardingCompleted = false;

    @CreationTimestamp 
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @UpdateTimestamp 
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private UserPreferences preference;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private NotificationSettings notificationSettings;
    
}
