package com.fintrack.api.dto.response.user;

import com.fintrack.api.entity.enums.UserRole;
import com.fintrack.api.entity.enums.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String username,
        String email,
        String displayName,
        UserRole role,
        UserStatus status,
        String currencyCode,
        String timeZone,
        Short weekStartsOn,
        Instant createdAt,
        Instant updatedAt
) {}
