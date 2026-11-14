package com.example.fintrack.app.budget.remote;

import com.example.fintrack.app.finance.domain.Budget;

import java.util.ArrayList;
import java.util.List;

/** POST /monthly-budgets: tháng + các dòng {categoryId, limitMinor}. Không có trường tổng (tổng là dẫn xuất). */
public class MonthlyBudgetRequest {
    public final String month;
    public final List<Line> categories = new ArrayList<>();

    public static final class Line {
        public final String categoryId;
        public final long limitMinor;
        Line(String categoryId, long limitMinor) { this.categoryId = categoryId; this.limitMinor = limitMinor; }
    }

    public MonthlyBudgetRequest(String month, List<Budget> lines) {
        this.month = month;
        for (Budget line : lines) categories.add(new Line(line.categoryId, line.limit));
    }
}
