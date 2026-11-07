package com.fintrack.api.dto.response.user;

import java.time.Instant;
import java.util.UUID;

public record UserPreferencesResponse(
        UUID userId,
        String displayName,
        String currencyCode,
        String timeZone,
        Short weekStartsOn,
        Instant updatedAt
) {}
