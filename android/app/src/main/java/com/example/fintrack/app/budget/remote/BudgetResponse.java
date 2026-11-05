package com.example.fintrack.app.budget.remote;

import com.example.fintrack.app.finance.domain.FinanceBook;
import com.google.gson.annotations.SerializedName;

/** Response DTO nhận dữ liệu Ngân sách trả về từ Server */
public class BudgetResponse {

    @SerializedName("budgetId")
    public String budgetId;

    @SerializedName("categoryId")
    public String categoryId;

    @SerializedName("month")
    public String month;

    @SerializedName("limitMinor")
    public long limitMinor;

    /** Chuyển đổi Response DTO sang mô hình dữ liệu Domain */
    public FinanceBook.Budget domain() {
        return new FinanceBook.Budget(budgetId, categoryId, month, limitMinor);
    }
}
