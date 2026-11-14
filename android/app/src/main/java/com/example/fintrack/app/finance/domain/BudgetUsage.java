package com.example.fintrack.app.finance.domain;

/** Immutable result of the shared budget policy. */
public final class BudgetUsage {
    public final long spent, remaining;
    public final String percentage;
    public final int progress;
    public final boolean warning, exceeded;
    public BudgetUsage(long spent,long limit,int threshold) {
        this.spent=spent; remaining=Math.subtractExact(limit,spent);
        java.math.BigInteger percent=BudgetPolicy.percentage(spent,limit);
        percentage=percent.toString();progress=percent.min(java.math.BigInteger.valueOf(100)).intValue();
        warning=BudgetPolicy.warning(spent,limit,threshold);
        exceeded=limit == 0 ? spent > 0 : spent >= limit;
    }
}
