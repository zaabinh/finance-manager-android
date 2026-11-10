package com.fintrack.api.dto.response.budget;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public record BudgetResponse(UUID budgetId, UUID monthlyBudgetId, UUID categoryId, String month, long limitMinor,
        long spentMinor, long remainingMinor, BigDecimal usagePercent, int warningThresholdPercent,
        String status, Instant archivedAt, Instant createdAt, Instant updatedAt) {
    public static BudgetResponse of(UUID id, UUID categoryId, String month, long limit, long spent,
            int threshold, Instant archivedAt, Instant createdAt, Instant updatedAt) {
        return of(id, null, categoryId, month, limit, spent, threshold, archivedAt, createdAt, updatedAt);
    }
    public static BudgetResponse of(UUID id, UUID monthlyBudgetId, UUID categoryId, String month, long limit, long spent,
            int threshold, Instant archivedAt, Instant createdAt, Instant updatedAt) {
        return new BudgetResponse(id, monthlyBudgetId, categoryId, month, limit, spent, Math.subtractExact(limit, spent),
                percent(limit, spent), threshold, status(limit, spent, threshold), archivedAt, createdAt, updatedAt);
    }
    /** Exact comparison before rounding. A zero limit (empty monthly budget) is SAFE until something is spent. */
    public static String status(long limit, long spent, int threshold) {
        if (limit == 0) return spent > 0 ? "EXCEEDED" : "SAFE";
        BigDecimal consumed = BigDecimal.valueOf(spent).multiply(BigDecimal.valueOf(100));
        return spent >= limit ? "EXCEEDED"
                : consumed.compareTo(BigDecimal.valueOf(limit).multiply(BigDecimal.valueOf(threshold))) >= 0 ? "WARNING" : "SAFE";
    }
    /** spent*100/limit, 2 decimals HALF_UP, not capped. A zero limit reports 0.00 (nothing spent) or 100.00. */
    public static BigDecimal percent(long limit, long spent) {
        if (limit == 0) return BigDecimal.valueOf(spent > 0 ? 100 : 0).setScale(2);
        return BigDecimal.valueOf(spent).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(limit), 2, RoundingMode.HALF_UP);
    }
}
