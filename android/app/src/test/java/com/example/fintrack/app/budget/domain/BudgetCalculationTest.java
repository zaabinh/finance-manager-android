package com.example.fintrack.app.budget.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.fintrack.app.finance.domain.Budget;

import org.junit.Test;

public class BudgetCalculationTest {

    @Test
    public void testBudgetCreationAndUsage() {
        Budget budget = new Budget("b1", "food", "2026-11", 5_000_000);
        assertEquals("b1", budget.id);
        assertEquals("food", budget.categoryId);
        assertEquals("2026-11", budget.month);
        assertEquals(5_000_000, budget.limit);

        Budget.BudgetUsage usageNormal = new Budget.BudgetUsage(2_000_000, 40, 40, 3_000_000, false, false);
        assertEquals(2_000_000, usageNormal.spent);
        assertEquals(3_000_000, usageNormal.remaining);
        assertFalse(usageNormal.warning);
        assertFalse(usageNormal.exceeded);

        Budget.BudgetUsage usageWarning = new Budget.BudgetUsage(4_200_000, 84, 84, 800_000, true, false);
        assertTrue(usageWarning.warning);
        assertFalse(usageWarning.exceeded);

        Budget.BudgetUsage usageExceeded = new Budget.BudgetUsage(5_500_000, 110, 100, -500_000, true, true);
        assertTrue(usageExceeded.warning);
        assertTrue(usageExceeded.exceeded);
    }
}
