package com.fintrack.api.dto.request.user;

import jakarta.validation.constraints.Size;

public record UpdatePreferencesRequest(
        @Size(max = 100, message = "TIME_ZONE_LENGTH_INVALID")
        String timeZone
) {}
