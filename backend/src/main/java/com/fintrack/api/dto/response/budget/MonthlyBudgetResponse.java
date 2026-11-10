package com.fintrack.api.dto.response.budget;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** totalLimitMinor is derived (sum of the category limits listed), never stored or entered. */
public record MonthlyBudgetResponse(UUID monthlyBudgetId, String month, long totalLimitMinor, long spentMinor,
        long remainingMinor, BigDecimal usagePercent, int warningThresholdPercent, String status,
        Instant archivedAt, Instant createdAt, Instant updatedAt, List<BudgetResponse> categories) {
    public static MonthlyBudgetResponse of(UUID id, String month, long total, long spent, int threshold,
            Instant archivedAt, Instant createdAt, Instant updatedAt, List<BudgetResponse> categories) {
        return new MonthlyBudgetResponse(id, month, total, spent, Math.subtractExact(total, spent),
                BudgetResponse.percent(total, spent), threshold, BudgetResponse.status(total, spent, threshold),
                archivedAt, createdAt, updatedAt, List.copyOf(categories));
    }
}
