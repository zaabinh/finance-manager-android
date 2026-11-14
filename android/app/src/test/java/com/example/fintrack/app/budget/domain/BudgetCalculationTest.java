package com.example.fintrack.app.budget.domain;
import static org.junit.Assert.*;
import com.example.fintrack.app.finance.domain.BudgetUsage;
import org.junit.Test;
public class BudgetCalculationTest {
    @Test public void calculatesNormalWarningAndExceededStates() {
        BudgetUsage normal=new BudgetUsage(2_000_000,5_000_000,80);
        assertEquals(3_000_000,normal.remaining);assertEquals("40",normal.percentage);assertFalse(normal.warning);
        assertTrue(new BudgetUsage(4_200_000,5_000_000,80).warning);
        BudgetUsage exceeded=new BudgetUsage(5_500_000,5_000_000,80);
        assertEquals(-500_000,exceeded.remaining);assertEquals(100,exceeded.progress);assertTrue(exceeded.exceeded);
    }
}
