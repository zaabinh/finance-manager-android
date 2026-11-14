package com.example.fintrack.app.finance.domain;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Extracted domain rules; no Android or persistence dependencies. */
final class BudgetPolicy {
    private BudgetPolicy() { }
    static void requireBalance(FinanceBook book, FinanceSnapshot.Builder state, String month, long before, long after) {
        if (after <= before) return;
        long stillToSpend = Math.max(0, Math.subtractExact(after, book.total(TransactionType.EXPENSE, month)));
        long available = book.spendable();
        if (stillToSpend > available) {
            throw new FinanceFailure(FinanceProblem.CONFLICT,FinanceBook.BUDGET_EXCEEDS_AVAILABLE_BALANCE,
                    Collections.emptyList(),Math.subtractExact(stillToSpend, available));
        }
    }
    static Budget validateCategoryBudget(FinanceBook book, FinanceSnapshot.Builder state,
                                         Budget value, boolean editing, boolean restoring) {
        if (value == null || value.id == null || value.id.isEmpty()) throw new FinanceFailure(FinanceProblem.NOT_FOUND);
        FinanceBook.amount(value.limit);
        validateMonth(value.month);
        Category category = book.category(value.categoryId);
        if (category == null || category.type != TransactionType.EXPENSE) throw new FinanceFailure(FinanceProblem.CATEGORY);
        if (value.monthlyBudgetId != null) {
            MonthlyBudget parent = book.monthlyBudget(value.monthlyBudgetId);
            if (parent == null || !parent.month.equals(value.month)) throw new FinanceFailure(restoring ? FinanceProblem.STORAGE : FinanceProblem.NOT_FOUND);
        }
        Budget old = book.budget(value.id);
        if ((editing && old == null) || (!editing && old != null)) throw new FinanceFailure(FinanceProblem.NOT_FOUND);
        if (!restoring && old != null && old.archivedAt != null) throw new FinanceFailure(FinanceProblem.ARCHIVED);
        if (!restoring && category.archivedAt != null && (old == null || !old.categoryId.equals(value.categoryId)))
            throw new FinanceFailure(FinanceProblem.ARCHIVED);

        // Kiểm tra tránh trùng lặp ngân sách cùng danh mục trong một tháng
        for (Budget item : state.budgets) {
            if (item.archivedAt == null && value.archivedAt == null && !item.id.equals(value.id) && item.categoryId.equals(value.categoryId) && item.month.equals(value.month))
                throw new FinanceFailure(FinanceProblem.DUPLICATE_BUDGET);
        }
        return old;
    }
    static void validateMonth(String month) {
        if (month == null || !month.matches("[0-9]{4}-(0[1-9]|1[0-2])")
                || Integer.parseInt(month.substring(0, 4)) < 1 || Integer.parseInt(month.substring(0, 4)) > 9998)
            throw new FinanceFailure(FinanceProblem.DATE);
    }
    static long allocation(FinanceBook book, List<Budget> categories) {
        if (categories == null || categories.isEmpty()) throw new FinanceFailure(FinanceProblem.VALIDATION);
        java.util.Set<String> seen = new java.util.HashSet<>();
        long total = 0;
        for (Budget line : categories) {
            if (line == null || line.categoryId == null || !seen.add(line.categoryId)) throw new FinanceFailure(FinanceProblem.VALIDATION);
            FinanceBook.amount(line.limit);
            Category category = book.category(line.categoryId);
            if (category == null || category.type != TransactionType.EXPENSE) throw new FinanceFailure(FinanceProblem.CATEGORY);
            if (category.archivedAt != null) throw new FinanceFailure(FinanceProblem.ARCHIVED);
            total = Math.addExact(total, line.limit);
        }
        return total;
    }
    static java.math.BigInteger percentage(long spent, long limit) {
        if (limit == 0) return java.math.BigInteger.valueOf(spent > 0 ? 100 : 0);
        return java.math.BigInteger.valueOf(spent).multiply(java.math.BigInteger.valueOf(100))
                .divide(java.math.BigInteger.valueOf(limit));
    }
    static boolean warning(long spent, long limit, int threshold) {
        if (limit == 0) return spent > 0;
        return java.math.BigInteger.valueOf(spent).multiply(java.math.BigInteger.valueOf(100))
                .compareTo(java.math.BigInteger.valueOf(limit).multiply(java.math.BigInteger.valueOf(threshold))) >= 0;
    }
    static void validateDeletion(MonthlyBudget monthly, String currentMonth) {
        if (monthly.month.equals(currentMonth)) throw new FinanceFailure(FinanceProblem.CONFLICT,
                FinanceBook.BUDGET_MONTH_CURRENT,Collections.emptyList(),null);
    }
}
