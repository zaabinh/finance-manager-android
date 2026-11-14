package com.example.fintrack.app.finance.domain;

/** Immutable finance entity. */
public final class MonthlyBudget {
    public final String id;
    public final String month;
    public final String archivedAt;
    public final Long serverTotalMinor;
    public final Long serverSpentMinor;
    public final int warningThresholdPercent;
    public MonthlyBudget(String id, String month, String archivedAt, Long serverTotalMinor, Long serverSpentMinor, int warningThresholdPercent) {
        this.id = id;
        this.month = month;
        this.archivedAt = archivedAt;
        this.serverTotalMinor = serverTotalMinor;
        this.serverSpentMinor = serverSpentMinor;
        this.warningThresholdPercent = warningThresholdPercent;
    }
    public MonthlyBudget(String id, String month) { this(id, month, null, null, null, 80); }
    public MonthlyBudget withArchivedAt(String value) {
        return new MonthlyBudget(id, month, value, serverTotalMinor, serverSpentMinor, warningThresholdPercent);
    }
}
