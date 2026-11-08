package com.fintrack.api.dto.request.user;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 100, message = "DISPLAY_NAME_LENGTH_INVALID")
        String displayName,
        @Size(max = 100, message = "TIME_ZONE_LENGTH_INVALID")
        String timeZone
) {}
