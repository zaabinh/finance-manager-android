package com.example.fintrack.app.home.domain;

import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.MonthlyBudget;
import com.example.fintrack.app.home.remote.DashboardResponse;

import java.math.BigInteger;

/** What Home renders, in integer VND. Plain Java: the server aggregate and the local ledger both map to this one shape. */
public final class HomeSummary {
    public final long totalAssets, available, savings, budgetLimit, budgetSpent;
    public final int budgetPercent;
    public final boolean budgetExceeded;

    public HomeSummary(long totalAssets, long available, long savings, long budgetLimit, long budgetSpent,
                       int budgetPercent, boolean budgetExceeded) {
        this.totalAssets = totalAssets;
        this.available = available;
        this.savings = savings;
        this.budgetLimit = budgetLimit;
        this.budgetSpent = budgetSpent;
        this.budgetPercent = budgetPercent;
        this.budgetExceeded = budgetExceeded;
    }

    /** Server aggregates are authoritative and shown as sent; any category over its limit counts as exceeded. */
    public static HomeSummary fromDashboard(DashboardResponse body) {
        DashboardResponse.Budget budget = body.budget;
        return new HomeSummary(body.assets.totalMinor, body.assets.availableMinor, body.assets.savingsMinor,
                budget.limitMinor, budget.spentMinor, budget.progressPercent,
                "EXCEEDED".equals(budget.worstStatus) || (budget.limitMinor > 0 && budget.spentMinor >= budget.limitMinor));
    }

    /** Mock flavor: derived from the ledger; savings are excluded from available. Throws ArithmeticException on overflow. */
    public static HomeSummary fromBook(FinanceBook book, long nowMillis) {
        String month = book.monthAt(nowMillis);
        MonthlyBudget budget = book.monthlyBudgetFor(month);
        long total = book.assets(false), savings = book.assets(true);
        long spent = budget == null ? book.budgetSpent(month) : book.monthlySpent(budget);
        long limit = budget == null ? 0 : book.monthlyTotal(budget);
        int percent = limit <= 0 ? 0 : BigInteger.valueOf(spent).multiply(BigInteger.valueOf(100))
                .divide(BigInteger.valueOf(limit)).min(BigInteger.valueOf(100)).intValue();
        return new HomeSummary(total, Math.subtractExact(total, savings), savings, limit, spent, percent, limit > 0 && spent >= limit);
    }

    public boolean hasBudget() { return budgetLimit > 0; }

    /** Money left under the limit; negative when over. */
    public long budgetRemaining() { return Math.subtractExact(budgetLimit, budgetSpent); }
}
