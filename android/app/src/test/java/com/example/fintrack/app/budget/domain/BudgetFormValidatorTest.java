package com.example.fintrack.app.budget.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.fintrack.app.budget.domain.BudgetFormValidator.Error;
import com.example.fintrack.app.budget.domain.BudgetFormValidator.Result;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class BudgetFormValidatorTest {
    private final AtomicInteger nextId = new AtomicInteger();

    private static Set<String> used(String... ids) { return new HashSet<>(Arrays.asList(ids)); }

    private static Result category(String category, Set<String> used, String amount) {
        return BudgetFormValidator.category("b1", "m1", category, "2026-11", used, amount);
    }

    private Result monthly(boolean exists, List<String> categories, List<String> limits) {
        return BudgetFormValidator.monthly("2026-12", exists, categories, limits, () -> "id" + nextId.incrementAndGet());
    }

    @Test public void validCategoryBudgetCarriesParentAndMonth() {
        Result result = category("food", used("rent"), "1.500.000");
        assertTrue(result.valid());
        assertEquals("b1", result.budget.id);
        assertEquals("food", result.budget.categoryId);
        assertEquals("2026-11", result.budget.month);
        assertEquals(1_500_000L, result.budget.limit);
        assertEquals("m1", result.budget.monthlyBudgetId);
    }

    @Test public void categoryIsRequiredAndUnique() {
        assertEquals(Error.MISSING_CATEGORY, category(null, used(), "1000").error);
        assertEquals(Error.MISSING_CATEGORY, category("  ", used(), "1000").error);
        assertEquals(Error.DUPLICATE_CATEGORY, category("food", used("food"), "1000").error);
        // The category check wins over a bad amount.
        assertEquals(Error.MISSING_CATEGORY, category(null, used(), "bad").error);
    }

    @Test public void categoryAmountRules() {
        for (String amount : new String[] {"", "0", "abc", "-1", "1000.000", "1.000.000.000.000"})
            assertEquals(amount, Error.INVALID_AMOUNT, category("food", used(), amount).error);
        assertEquals(Error.INVALID_AMOUNT, category("food", used(), null).error);
        assertTrue(category("food", used(), "999999999999").valid());
    }

    @Test public void monthlyBudgetKeepsEachCategoryOnce() {
        Result result = monthly(false, Arrays.asList("food", "rent", "food"), Arrays.asList("1.000.000", " 2.000.000 ", "3000"));
        assertTrue(result.valid());
        assertNull(result.budget);
        assertEquals(2, result.lines.size());
        assertEquals("food", result.lines.get(0).categoryId);
        assertEquals(1_000_000L, result.lines.get(0).limit);
        assertEquals("rent", result.lines.get(1).categoryId);
        assertEquals(2_000_000L, result.lines.get(1).limit);
        assertEquals("2026-12", result.lines.get(1).month);
    }

    @Test public void monthlyBudgetNeedsRowsAndAFreeMonth() {
        List<String> none = Collections.emptyList();
        assertEquals(Error.NO_CATEGORIES, monthly(false, none, none).error);
        // Missing rows win over an existing month, like the original toast order.
        assertEquals(Error.NO_CATEGORIES, monthly(true, none, none).error);
        assertEquals(Error.MONTH_EXISTS, monthly(true, Arrays.asList("food"), Arrays.asList("1000")).error);
    }

    @Test public void monthlyBudgetReportsTheFirstInvalidRow() {
        Result result = monthly(false, Arrays.asList("food", "rent", "fun"), Arrays.asList("1000", "", "bad"));
        assertEquals(Error.INVALID_AMOUNT, result.error);
        assertEquals(1, result.row);
        assertNull(result.lines);
    }
}
