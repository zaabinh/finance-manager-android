package com.example.fintrack.app.budget.domain;

import static org.junit.Assert.*;

import com.example.fintrack.app.finance.domain.Budget;
import com.example.fintrack.app.finance.domain.BudgetUsage;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.MonthlyBudget;
import com.example.fintrack.app.finance.domain.Transaction;
import com.example.fintrack.app.finance.domain.TransactionType;
import com.example.fintrack.app.finance.domain.WalletType;
import java.util.Arrays;
import java.util.Calendar;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

/** Budget usage as the screens get it: spent derived from the book's transactions, against the budget's limit. */
public class BudgetCalculationTest {
    private FinanceBook book;
    private String cash, savings;
    private long october;
    private MonthlyBudget monthly;
    private Budget food;

    @Before public void setup() {
        book = new FinanceBook(FinanceBook.empty());
        cash = book.addWallet("Tiền mặt", 10_000_000).id;
        savings = book.addWallet("Tiết kiệm", 5_000_000, WalletType.SAVINGS).id;
        Calendar calendar = Calendar.getInstance(FinanceBook.ZONE); calendar.clear(); calendar.set(2026, Calendar.OCTOBER, 15, 12, 0);
        october = calendar.getTimeInMillis();
        monthly = book.createMonthlyBudget("m", "2026-10", Arrays.asList(
                new Budget(null, "food", "2026-10", 1_000_000), new Budget(null, "bills", "2026-10", 500_000)));
        for (Budget line : book.categoryBudgets(monthly)) if (line.categoryId.equals("food")) food = line;
    }

    private String spend(TransactionType type, long amount, String category) {
        String id = UUID.randomUUID().toString();
        book.save(new Transaction(id, type, amount, category, cash, type == TransactionType.TRANSFER ? savings : null, october, "", ""), false);
        return id;
    }

    @Test public void spendingExactlyTheLimitIsExceededWithNothingRemaining() {
        spend(TransactionType.EXPENSE, 1_000_000, "food");
        BudgetUsage usage = book.usage(food);
        assertEquals(1_000_000, usage.spent);
        assertEquals(0, usage.remaining);
        assertEquals("100", usage.percentage);
        assertEquals(100, usage.progress);
        assertTrue(usage.exceeded);
        assertTrue(usage.warning);
    }

    @Test public void overspendGoesNegativeAndCapsTheProgressBar() {
        spend(TransactionType.EXPENSE, 1_250_000, "food");
        BudgetUsage usage = book.usage(food);
        assertEquals(-250_000, usage.remaining);
        assertEquals("125", usage.percentage);
        assertEquals(100, usage.progress);
        assertTrue(usage.exceeded);
    }

    @Test public void warningStartsAtTheThresholdPercent() {
        spend(TransactionType.EXPENSE, 799_999, "food");
        BudgetUsage below = book.usage(food);
        assertEquals("79", below.percentage);
        assertFalse(below.warning);
        assertFalse(below.exceeded);

        spend(TransactionType.EXPENSE, 1, "food"); // exactly 80% of the limit
        BudgetUsage at = book.usage(food);
        assertEquals(200_000, at.remaining);
        assertTrue(at.warning);
        assertFalse(at.exceeded);
    }

    @Test public void onlyPostedExpensesOfTheBudgetCategoryCount() {
        spend(TransactionType.EXPENSE, 300_000, "food");
        spend(TransactionType.EXPENSE, 200_000, "transport");
        spend(TransactionType.INCOME, 900_000, "salary");
        spend(TransactionType.TRANSFER, 400_000, null);
        book.deleteTransaction(spend(TransactionType.EXPENSE, 600_000, "food")); // voided

        assertEquals(300_000, book.usage(food).spent);
        // The monthly budget counts every posted expense of the month, in any category; transfers never count.
        BudgetUsage month = book.monthlyUsage(monthly);
        assertEquals(500_000, month.spent);
        assertEquals(1_000_000, month.remaining); // total = 1.000.000 + 500.000 of category limits
        assertEquals("33", month.percentage);
        assertFalse(month.warning);
    }

    @Test public void zeroLimitIsExceededByAnySpending() {
        // A server-provided total of 0 (e.g. every category limit removed on another device).
        MonthlyBudget empty = new MonthlyBudget("zero", "2026-10", null, 0L, null, 80);
        BudgetUsage none = book.monthlyUsage(empty);
        assertEquals("0", none.percentage);
        assertFalse(none.warning);
        assertFalse(none.exceeded);

        spend(TransactionType.EXPENSE, 1, "food");
        BudgetUsage some = book.monthlyUsage(empty);
        assertEquals(-1, some.remaining);
        assertEquals("100", some.percentage);
        assertTrue(some.warning);
        assertTrue(some.exceeded);
    }
}
