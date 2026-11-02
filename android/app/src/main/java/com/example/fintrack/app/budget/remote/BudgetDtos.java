package com.example.fintrack.app.budget.remote;

import com.example.fintrack.app.finance.domain.FinanceBook;

public final class BudgetDtos {

    public static final class Budget {
        public String budgetId, categoryId, month;
        public long limitMinor;

        public FinanceBook.Budget domain() {
            return new FinanceBook.Budget(budgetId, categoryId, month, limitMinor);
        }
    }

    public static final class BudgetRequest {
        public final String categoryId, month;
        public final long limitMinor;

        public BudgetRequest(FinanceBook.Budget value) {
            categoryId = value.categoryId;
            month = value.month;
            limitMinor = value.limit;
        }
    }

    private BudgetDtos() { }
}
