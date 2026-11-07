package com.fintrack.api.dto.request.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

// wallets will be added here once the wallet API exists.
public record FirstSetupRequest(
        @NotNull(message = "PROFILE_REQUIRED") @Valid UpdateProfileRequest profile,
        @Valid UpdateNotificationSettingsRequest notificationSettings
) {}
