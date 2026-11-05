package com.example.fintrack.app.budget.remote;

import com.example.fintrack.app.finance.domain.FinanceBook;
import com.google.gson.annotations.SerializedName;

/** Request DTO đóng gói dữ liệu khi Tạo hoặc Sửa Ngân sách gửi lên Server */
public class BudgetRequest {

    @SerializedName("categoryId")
    public final String categoryId;

    @SerializedName("month")
    public final String month;

    @SerializedName("limitMinor")
    public final long limitMinor;

    public BudgetRequest(FinanceBook.Budget value) {
        categoryId = value.categoryId;
        month = value.month;
        limitMinor = value.limit;
    }
}
