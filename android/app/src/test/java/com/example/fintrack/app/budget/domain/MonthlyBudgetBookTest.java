package com.example.fintrack.app.budget.domain;

import com.example.fintrack.app.finance.domain.Budget;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.FinanceFailure;
import com.example.fintrack.app.finance.domain.FinanceProblem;
import com.example.fintrack.app.finance.domain.FinanceSnapshot;
import com.example.fintrack.app.finance.domain.MonthlyBudget;
import com.example.fintrack.app.finance.domain.Transaction;
import com.example.fintrack.app.finance.domain.TransactionType;
import com.example.fintrack.app.finance.domain.WalletType;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

/** Mock-flavor parity of the hierarchical budget rules (spec 2026-11-09 §1). */
public class MonthlyBudgetBookTest {
    private FinanceBook book;
    private String cash, savings;
    private long october;

    @Before public void setup() {
        book = new FinanceBook(FinanceBook.empty());
        cash = book.addWallet("Tiền mặt", 1_000_000).id;
        savings = book.addWallet("Tiết kiệm", 5_000_000, WalletType.SAVINGS).id;
        Calendar calendar = Calendar.getInstance(FinanceBook.ZONE); calendar.clear(); calendar.set(2026, Calendar.OCTOBER, 15, 12, 0);
        october = calendar.getTimeInMillis();
    }
    private static Budget line(String category, long limit) {
        return new Budget(UUID.randomUUID().toString(), category, "2026-10", limit);
    }
    private Transaction tx(TransactionType type, long amount, String category, String from, String to) {
        return new Transaction(UUID.randomUUID().toString(), type, amount, category, from, to, october, "", "");
    }
    private static FinanceFailure failure(Runnable call) { return assertThrows(FinanceFailure.class, call::run); }

    @Test public void totalIsDerivedFromCategoryLimitsAndFollowsAddEditRemove() {
        MonthlyBudget monthly = book.createMonthlyBudget("m", "2026-10", Arrays.asList(line("food", 300_000), line("bills", 200_000)));
        assertEquals(500_000, book.monthlyTotal(monthly));
        assertEquals(2, book.categoryBudgets(monthly).size());
        book.saveCategoryBudget(new Budget("t", "transport", "2026-10", 100_000), false);
        assertEquals(600_000, book.monthlyTotal(monthly));
        book.saveCategoryBudget(new Budget("t", "transport", "2026-10", 50_000), true);
        assertEquals(550_000, book.monthlyTotal(monthly));
        for (Budget child : book.categoryBudgets(monthly)) book.deleteBudget(child.id);
        // Removing the last category keeps the monthly budget with total 0 (SAFE while nothing is spent).
        assertSame(monthly, book.monthlyBudgetFor("2026-10"));
        assertEquals(0, book.monthlyTotal(monthly));
        assertFalse(book.monthlyUsage(monthly).exceeded);
    }

    @Test public void monthlySpentCountsAllExpensesButNeverTransfersOrVoids() {
        MonthlyBudget monthly = book.createMonthlyBudget("m", "2026-10", Collections.singletonList(line("food", 300_000)));
        book.save(tx(TransactionType.EXPENSE, 40_000, "food", cash, null), false);
        book.save(tx(TransactionType.EXPENSE, 10_000, "shopping", cash, null), false); // category without budget
        book.save(tx(TransactionType.TRANSFER, 70_000, null, savings, cash), false);
        book.save(tx(TransactionType.INCOME, 90_000, "salary", cash, null), false);
        Transaction voided = tx(TransactionType.EXPENSE, 5_000, "food", cash, null);
        book.save(voided, false); book.deleteTransaction(voided.id);
        assertEquals(50_000, book.monthlySpent(monthly));
        assertEquals(40_000, book.spent(book.categoryBudgets(monthly).get(0)));
        assertEquals(250_000, book.monthlyUsage(monthly).remaining);
    }

    @Test public void createIsAtomicAndNeedsDistinctActiveExpenseCategories() {
        assertEquals(FinanceProblem.VALIDATION, failure(() -> book.createMonthlyBudget("m", "2026-10", Collections.emptyList())).problem);
        assertEquals(FinanceProblem.VALIDATION, failure(() -> book.createMonthlyBudget("m", "2026-10", Arrays.asList(line("food", 1), line("food", 2)))).problem);
        assertEquals(FinanceProblem.CATEGORY, failure(() -> book.createMonthlyBudget("m", "2026-10", Arrays.asList(line("food", 1), line("salary", 1)))).problem);
        assertTrue(book.snapshot().monthlyBudgets.isEmpty());
        assertTrue(book.snapshot().budgets.isEmpty());
        book.createMonthlyBudget("m", "2026-10", Collections.singletonList(line("food", 1)));
        assertEquals(FinanceBook.BUDGET_PERIOD_CONFLICT, failure(() -> book.createMonthlyBudget("n", "2026-10", Collections.singletonList(line("bills", 1)))).code);
    }

    @Test public void categoryBudgetRequiresMonthlyParent() {
        FinanceFailure missing = failure(() -> book.saveCategoryBudget(new Budget("b", "food", "2026-11", 1), false));
        assertEquals(FinanceProblem.CONFLICT, missing.problem);
        assertEquals(FinanceBook.BUDGET_MONTH_MISSING, missing.code);
        assertTrue(book.snapshot().budgets.isEmpty());
    }

    @Test public void increaseBeyondSpendableBalanceIsBlockedWithShortfallAndDecreaseIsNot() {
        // Spendable = cash 1 000 000 (savings excluded).
        FinanceFailure create = failure(() -> book.createMonthlyBudget("m", "2026-10", Collections.singletonList(line("food", 1_200_000))));
        assertEquals(FinanceBook.BUDGET_EXCEEDS_AVAILABLE_BALANCE, create.code);
        assertEquals(Long.valueOf(200_000), create.shortfallMinor);
        assertTrue(book.snapshot().monthlyBudgets.isEmpty());

        MonthlyBudget monthly = book.createMonthlyBudget("m", "2026-10", Collections.singletonList(line("food", 900_000)));
        FinanceFailure add = failure(() -> book.saveCategoryBudget(new Budget("b", "bills", "2026-10", 100_001), false));
        assertEquals(Long.valueOf(1), add.shortfallMinor);
        assertEquals(900_000, book.monthlyTotal(monthly));
        assertNull(book.budget("b"));

        // Spending already done reduces what is still to spend: 900 000 total, 400 000 spent -> 500 000 still to spend.
        book.save(tx(TransactionType.EXPENSE, 400_000, "food", cash, null), false);
        book.saveCategoryBudget(new Budget("b", "bills", "2026-10", 100_000), false);
        assertEquals(1_000_000, book.monthlyTotal(monthly));

        // Decreases are never blocked, even with nothing spendable left.
        book.save(tx(TransactionType.EXPENSE, 600_000, "shopping", cash, null), false);
        assertEquals(0, book.spendable());
        book.saveCategoryBudget(new Budget("b", "bills", "2026-10", 10), true);
        assertEquals(900_010, book.monthlyTotal(monthly));
    }

    @Test public void currentMonthCannotBeDeletedAndDeleteArchivesChildren() {
        long now = System.currentTimeMillis();
        String current = book.monthAt(now);
        book.createMonthlyBudget("current", current, Collections.singletonList(new Budget("c", "food", current, 1)));
        assertEquals(FinanceBook.BUDGET_MONTH_CURRENT, failure(() -> book.deleteMonthlyBudget("current", now)).code);

        MonthlyBudget past = book.createMonthlyBudget("past", "2026-10", Arrays.asList(line("food", 1), line("bills", 2)));
        book.deleteMonthlyBudget("past", now);
        book.deleteMonthlyBudget("past", now);
        assertNull(past.archivedAt); // Previously returned values remain unchanged.
        past = book.monthlyBudget("past");
        assertNotNull(past.archivedAt);
        assertNull(book.monthlyBudgetFor("2026-10"));
        assertEquals(2, book.categoryBudgets(past).size());
        for (Budget child : book.categoryBudgets(past)) assertEquals(past.archivedAt, child.archivedAt);
        assertTrue(book.budgets("2026-10").isEmpty());
        // The month is free again after deletion.
        book.createMonthlyBudget("again", "2026-10", Collections.singletonList(line("food", 5)));
    }

    @Test public void serverAggregatesWinOverLocalDerivation() {
        FinanceSnapshot.Builder state = FinanceBook.empty();
        MonthlyBudget monthly = new MonthlyBudget("m", "2026-10", null, 700_000L, 650_000L, 90);
        state.monthlyBudgets.add(monthly);
        Budget child = new Budget("b", "food", "2026-10", 700_000, 1L, 80, null, "m");
        state.budgets.add(child);
        FinanceBook server = new FinanceBook(state);
        MonthlyBudget restored = server.monthlyBudgetFor("2026-10");
        assertEquals(700_000, server.monthlyTotal(restored));
        assertEquals(650_000, server.monthlySpent(restored));
        assertTrue(server.monthlyUsage(restored).warning);
        assertEquals(1, server.monthlyBudgets().size());
    }

    @Test public void restoreRejectsTwoActiveMonthlyBudgetsForOneMonthOrOrphanParentLink() {
        FinanceSnapshot.Builder twice = FinanceBook.empty();
        twice.monthlyBudgets.add(new MonthlyBudget("a", "2026-10"));
        twice.monthlyBudgets.add(new MonthlyBudget("b", "2026-10"));
        assertEquals(FinanceProblem.STORAGE, failure(() -> new FinanceBook(twice)).problem);
        FinanceSnapshot.Builder orphan = FinanceBook.empty();
        Budget child = new Budget("x", "food", "2026-10", 1).withMonthlyBudgetId("missing");
        orphan.budgets.add(child);
        assertEquals(FinanceProblem.STORAGE, failure(() -> new FinanceBook(orphan)).problem);
    }
}
