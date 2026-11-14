package com.example.fintrack.app.budget.remote;

import com.example.fintrack.app.finance.domain.Budget;
import com.example.fintrack.app.finance.domain.FinanceFailure;
import com.example.fintrack.app.finance.domain.FinanceProblem;

import com.google.gson.annotations.SerializedName;

/** Response DTO nhận dữ liệu Ngân sách trả về từ Server */
public class BudgetResponse {

    @SerializedName("budgetId")
    public String budgetId;

    @SerializedName("monthlyBudgetId")
    public String monthlyBudgetId;

    @SerializedName("categoryId")
    public String categoryId;

    @SerializedName("month")
    public String month;

    @SerializedName("limitMinor")
    public long limitMinor;

    public Long spentMinor;
    public long remainingMinor;
    public java.math.BigDecimal usagePercent;
    public int warningThresholdPercent = 80;
    public String status, archivedAt, createdAt, updatedAt;

    /** Chuyển đổi Response DTO sang mô hình dữ liệu Domain */
    public Budget domain() {
        if (spentMinor == null || spentMinor < 0 || warningThresholdPercent < 1 || warningThresholdPercent > 100)
            throw new FinanceFailure(FinanceProblem.SERVER);
        return new Budget(budgetId,categoryId,month,limitMinor,spentMinor,warningThresholdPercent,archivedAt,monthlyBudgetId);
    }
}
