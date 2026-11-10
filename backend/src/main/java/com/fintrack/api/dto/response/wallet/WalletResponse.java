package com.fintrack.api.dto.response.wallet;
import java.time.Instant;
import java.util.UUID;
public record WalletResponse(UUID walletId, String name, String walletType, String currencyCode,
        long openingBalanceMinor, long balanceMinor, boolean includeInAssets, int sortOrder,
        Instant archivedAt, Instant createdAt, Instant updatedAt) {}
