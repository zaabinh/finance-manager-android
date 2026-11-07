package com.fintrack.api.dto.response.transaction;

import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(UUID transactionId, String transactionType, long amountMinor, UUID categoryId,
        UUID walletId, UUID destinationWalletId, Instant occurredAt, String note, String payee, UUID clientRequestId,
        String status, Instant voidedAt, Instant createdAt, Instant updatedAt) {}
