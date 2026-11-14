package com.example.fintrack.app.budget.domain;

import com.example.fintrack.app.finance.domain.Budget;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.FinanceFailure;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** Client-side checks of the budget forms; the authoritative rules stay in {@link FinanceBook}. */
public final class BudgetFormValidator {
    private BudgetFormValidator() { }

    public enum Error { MISSING_CATEGORY, DUPLICATE_CATEGORY, INVALID_AMOUNT, NO_CATEGORIES, MONTH_EXISTS }

    public static final class Result {
        /** Category budget form: the validated value, null on error. */
        public final Budget budget;
        /** Monthly form: one line per distinct category, null on error. */
        public final List<Budget> lines;
        public final Error error;
        /** Monthly form with INVALID_AMOUNT: index of the offending row, otherwise -1. */
        public final int row;
        private Result(Budget budget, List<Budget> lines, Error error, int row) {
            this.budget = budget; this.lines = lines; this.error = error; this.row = row;
        }
        public boolean valid() { return error == null; }
    }

    /** One category's limit inside a monthly budget; usedCategories excludes the budget being edited. */
    public static Result category(String id, String monthlyId, String category, String month,
            Set<String> usedCategories, String amountText) {
        if (category == null || category.trim().isEmpty()) return fail(Error.MISSING_CATEGORY, -1);
        if (usedCategories.contains(category)) return fail(Error.DUPLICATE_CATEGORY, -1);
        long limit;
        try { limit = FinanceBook.parseAmount(amountText); }
        catch (FinanceFailure error) { return fail(Error.INVALID_AMOUNT, -1); }
        return new Result(new Budget(id, category, month, limit).withMonthlyBudgetId(monthlyId), null, null, -1);
    }

    /** A new monthly budget: rows are parallel category/limit lists; a repeated category is kept once. */
    public static Result monthly(String month, boolean monthExists, List<String> categories, List<String> limits,
            Supplier<String> ids) {
        if (categories.isEmpty()) return fail(Error.NO_CATEGORIES, -1);
        if (monthExists) return fail(Error.MONTH_EXISTS, -1);
        List<Budget> lines = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < categories.size(); i++) {
            long limit;
            try { limit = FinanceBook.parseAmount(limits.get(i).trim()); }
            catch (FinanceFailure error) { return fail(Error.INVALID_AMOUNT, i); }
            if (!seen.add(categories.get(i))) continue;
            lines.add(new Budget(ids.get(), categories.get(i), month, limit));
        }
        return new Result(null, lines, null, -1);
    }

    private static Result fail(Error error, int row) { return new Result(null, null, error, row); }
}
