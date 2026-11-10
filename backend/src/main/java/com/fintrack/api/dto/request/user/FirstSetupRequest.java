package com.fintrack.api.dto.request.user;

import com.fintrack.api.dto.request.wallet.WalletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** wallets: optional, created in the same transaction as the setup (same rules as POST /api/v1/wallets). */
public record FirstSetupRequest(
        @NotNull(message = "PROFILE_REQUIRED") @Valid UpdateProfileRequest profile,
        @Valid UpdateNotificationSettingsRequest notificationSettings,
        List<WalletRequest> wallets
) {}
