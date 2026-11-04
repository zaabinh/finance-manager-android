package com.example.fintrack.app.budget.model;

public final class Budget {
    public String id, categoryId, month;
    public long limit;

    public Budget(String id, String categoryId, String month, long limit) {
        this.id = id;
        this.categoryId = categoryId;
        this.month = month;
        this.limit = limit;
    }

    public static final class BudgetUsage {
        public final long spent;
        public final int percentage;
        public final int progress;
        public final long remaining;
        public final boolean warning;
        public final boolean exceeded;

        public BudgetUsage(long spent, int percentage, int progress, long remaining, boolean warning, boolean exceeded) {
            this.spent = spent;
            this.percentage = percentage;
            this.progress = progress;
            this.remaining = remaining;
            this.warning = warning;
            this.exceeded = exceeded;
        }
    }
}
