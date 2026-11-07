package com.fintrack.api.dto.response.budget;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public record BudgetResponse(UUID budgetId, UUID categoryId, String month, long limitMinor,
        long spentMinor, long remainingMinor, BigDecimal usagePercent, int warningThresholdPercent,
        String status, Instant archivedAt, Instant createdAt, Instant updatedAt) {
    public static BudgetResponse of(UUID id, UUID categoryId, String month, long limit, long spent,
            int threshold, Instant archivedAt, Instant createdAt, Instant updatedAt) {
        BigDecimal consumed = BigDecimal.valueOf(spent).multiply(BigDecimal.valueOf(100));
        BigDecimal ceiling = BigDecimal.valueOf(limit);
        String status = spent >= limit ? "EXCEEDED"
                : consumed.compareTo(ceiling.multiply(BigDecimal.valueOf(threshold))) >= 0 ? "WARNING" : "SAFE";
        return new BudgetResponse(id, categoryId, month, limit, spent, Math.subtractExact(limit, spent),
                consumed.divide(ceiling, 2, RoundingMode.HALF_UP), threshold, status,
                archivedAt, createdAt, updatedAt);
    }
}
