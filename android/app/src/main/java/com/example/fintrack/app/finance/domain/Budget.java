package com.example.fintrack.app.finance.domain;

/** Immutable finance entity. */
public final class Budget {
    public final String id;
    public final String categoryId;
    public final String month;
    public final long limit;
    public final Long serverSpentMinor;
    public final int warningThresholdPercent;
    public final String archivedAt;
    public final String monthlyBudgetId;
    public Budget(String id, String categoryId, String month, long limit, Long serverSpentMinor, int warningThresholdPercent, String archivedAt, String monthlyBudgetId) {
        this.id = id;
        this.categoryId = categoryId;
        this.month = month;
        this.limit = limit;
        this.serverSpentMinor = serverSpentMinor;
        this.warningThresholdPercent = warningThresholdPercent;
        this.archivedAt = archivedAt;
        this.monthlyBudgetId = monthlyBudgetId;
    }
    public Budget(String id, String categoryId, String month, long limit) { this(id, categoryId, month, limit, null, 80, null, null); }
    public Budget withMonthlyBudgetId(String value) {
        return new Budget(id, categoryId, month, limit, serverSpentMinor, warningThresholdPercent, archivedAt, value);
    }
    public Budget withArchivedAt(String value) {
        return new Budget(id, categoryId, month, limit, serverSpentMinor, warningThresholdPercent, value, monthlyBudgetId);
    }
    public Budget withServerSpentMinor(Long value) {
        return new Budget(id, categoryId, month, limit, value, warningThresholdPercent, archivedAt, monthlyBudgetId);
    }
}
