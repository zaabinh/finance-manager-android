package com.example.fintrack.app.budget.remote;

import com.example.fintrack.app.finance.domain.Budget;
import com.example.fintrack.app.finance.domain.FinanceFailure;
import com.example.fintrack.app.finance.domain.FinanceProblem;
import com.example.fintrack.app.finance.domain.FinanceSnapshot;
import com.example.fintrack.app.finance.domain.MonthlyBudget;

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
    public MonthlyBudget domain() {
        if (monthlyBudgetId == null || month == null || totalLimitMinor == null || totalLimitMinor < 0
                || spentMinor == null || spentMinor < 0 || categories == null
                || warningThresholdPercent < 1 || warningThresholdPercent > 100)
            throw new FinanceFailure(FinanceProblem.SERVER);
        return new MonthlyBudget(monthlyBudgetId,month,archivedAt,totalLimitMinor,spentMinor,warningThresholdPercent);
    }

    /** Adds the monthly budget and its category budgets to a server snapshot state. */
    public void into(FinanceSnapshot.Builder state) {
        state.monthlyBudgets.add(domain());
        for (BudgetResponse item : categories) {
            if (item == null) throw new FinanceFailure(FinanceProblem.SERVER);
            Budget budget = item.domain();
            if (budget.monthlyBudgetId == null) budget = budget.withMonthlyBudgetId(monthlyBudgetId);
            if (!monthlyBudgetId.equals(budget.monthlyBudgetId)) throw new FinanceFailure(FinanceProblem.SERVER);
            state.budgets.add(budget);
        }
    }
}
