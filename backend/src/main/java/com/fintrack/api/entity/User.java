package com.fintrack.api.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users", schema = "fintrack")
@Getter
@NoArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, columnDefinition = "citext") private String username;
    @Column(nullable = false, columnDefinition = "citext") private String email;
    @Column(name = "password_hash", nullable = false, columnDefinition = "text") private String passwordHash;
    @Column(nullable = false, columnDefinition = "text") private String role;
    @Column(nullable = false, columnDefinition = "text") private String status;
    @Column(name = "email_verified", nullable = false) private boolean emailVerified;
    @Column(name = "onboarding_completed", nullable = false) private boolean onboardingCompleted;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}
