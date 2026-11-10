package com.example.fintrack.app.budget.remote;

import com.example.fintrack.app.finance.domain.FinanceBook;
import java.util.List;

/** Ngân sách tháng từ server: tổng (dẫn xuất), đã chi, còn lại và các ngân sách danh mục. */
public class MonthlyBudgetResponse {
    public String monthlyBudgetId, month;
    public Long totalLimitMinor, spentMinor;
    public long remainingMinor;
    public java.math.BigDecimal usagePercent;
    public int warningThresholdPercent = 80;
    public String status, archivedAt, createdAt, updatedAt;
    public List<BudgetResponse> categories;

    /** Server values are authoritative; a response without aggregates is rejected rather than recomputed. */
    public FinanceBook.MonthlyBudget domain() {
        if (monthlyBudgetId == null || month == null || totalLimitMinor == null || totalLimitMinor < 0
                || spentMinor == null || spentMinor < 0 || categories == null
                || warningThresholdPercent < 1 || warningThresholdPercent > 100)
            throw new FinanceBook.Failure(FinanceBook.Problem.SERVER);
        FinanceBook.MonthlyBudget value = new FinanceBook.MonthlyBudget(monthlyBudgetId, month);
        value.serverTotalMinor = totalLimitMinor;
        value.serverSpentMinor = spentMinor;
        value.warningThresholdPercent = warningThresholdPercent;
        value.archivedAt = archivedAt;
        return value;
    }

    /** Adds the monthly budget and its category budgets to a server snapshot state. */
    public void into(FinanceBook.State state) {
        state.monthlyBudgets.add(domain());
        for (BudgetResponse item : categories) {
            if (item == null) throw new FinanceBook.Failure(FinanceBook.Problem.SERVER);
            FinanceBook.Budget budget = item.domain();
            if (budget.monthlyBudgetId == null) budget.monthlyBudgetId = monthlyBudgetId;
            if (!monthlyBudgetId.equals(budget.monthlyBudgetId)) throw new FinanceBook.Failure(FinanceBook.Problem.SERVER);
            state.budgets.add(budget);
        }
    }
}
