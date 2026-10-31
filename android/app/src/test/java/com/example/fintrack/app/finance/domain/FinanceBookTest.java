package com.example.fintrack.app.finance.domain;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.UUID;

public class FinanceBookTest {
    private FinanceBook book;
    private String cash, bank;
    private long october;
    @Before public void setup() {
        book = new FinanceBook(FinanceBook.empty());
        cash = book.addWallet("Tiền mặt", 1_000_000).id;
        bank = book.addWallet("Ngân hàng", 2_000_000, FinanceBook.WalletType.BANK).id;
        Calendar calendar = Calendar.getInstance(FinanceBook.ZONE); calendar.clear(); calendar.set(2026, Calendar.OCTOBER, 15, 12, 0);
        october = calendar.getTimeInMillis();
    }
    private FinanceBook.Transaction transaction(FinanceBook.Type type, long amount, String category, String from, String to) {
        return new FinanceBook.Transaction(UUID.randomUUID().toString(), type, amount, category, from, to, october, "Cà phê sáng", "Quán Đồng");
    }
    private void problem(FinanceBook.Problem problem, Runnable call) {
        assertEquals(problem, assertThrows(FinanceBook.Failure.class, call::run).problem);
    }
    @Test public void expenseAndIncomeUpdateDerivedBalances() {
        book.save(transaction(FinanceBook.Type.EXPENSE, 95_000, "food", cash, null), false);
        book.save(transaction(FinanceBook.Type.INCOME, 5_000_000, "salary", bank, null), false);
        assertEquals(905_000, book.balance(cash)); assertEquals(7_000_000, book.balance(bank));
        assertEquals(95_000, book.total(FinanceBook.Type.EXPENSE, "2026-10"));
        assertEquals(5_000_000, book.total(FinanceBook.Type.INCOME, "2026-10"));
    }
    @Test public void transferPreservesAssetsAndNeverConsumesBudget() {
        FinanceBook.Budget budget = new FinanceBook.Budget("b", "food", "2026-10", 500_000); book.save(budget, false);
        book.save(transaction(FinanceBook.Type.TRANSFER, 400_000, "food", cash, bank), false);
        assertEquals(600_000, book.balance(cash)); assertEquals(2_400_000, book.balance(bank));
        assertEquals(3_000_000, book.assets(false)); assertEquals(0, book.spent(budget));
        assertEquals(0, book.total(FinanceBook.Type.EXPENSE, "2026-10")); assertEquals(0, book.total(FinanceBook.Type.INCOME, "2026-10"));
    }
    @Test public void editingReversesOldPostingAcrossWalletsAndTypes() {
        FinanceBook.Transaction old = transaction(FinanceBook.Type.EXPENSE, 80_000, "food", cash, null); book.save(old, false);
        book.save(new FinanceBook.Transaction(old.id, FinanceBook.Type.INCOME, 120_000, "salary", bank, null, october, "", ""), true);
        assertEquals(1_000_000, book.balance(cash)); assertEquals(2_120_000, book.balance(bank));
        assertEquals(0, book.total(FinanceBook.Type.EXPENSE, "2026-10"));
    }
    @Test public void editingTransferReversesBothOldEntries() {
        FinanceBook.Transaction old = transaction(FinanceBook.Type.TRANSFER, 100_000, null, cash, bank); book.save(old, false);
        book.save(new FinanceBook.Transaction(old.id, FinanceBook.Type.TRANSFER, 300_000, null, bank, cash, october, "", ""), true);
        assertEquals(1_300_000, book.balance(cash)); assertEquals(1_700_000, book.balance(bank));
        book.deleteTransaction(old.id); assertEquals(3_000_000, book.assets(false)); assertEquals(1_000_000, book.balance(cash));
    }
    @Test public void deleteRecalculatesBudgetAndKeepsOtherTransactions() {
        FinanceBook.Budget budget = new FinanceBook.Budget("b", "food", "2026-10", 100_000); book.save(budget, false);
        FinanceBook.Transaction first = transaction(FinanceBook.Type.EXPENSE, 95_000, "food", cash, null);
        book.save(first, false); book.save(transaction(FinanceBook.Type.EXPENSE, 15_000, "food", cash, null), false);
        assertTrue(book.usage(budget).exceeded); assertEquals(-10_000, book.usage(budget).remaining);
        book.deleteTransaction(first.id); assertEquals(15_000, book.spent(budget)); assertFalse(book.usage(budget).warning);
        book.deleteBudget(budget.id); assertEquals(1, book.state().transactions.size());
    }
    @Test public void budgetsUseExactCategoryAndMonthAndExpenseOnly() {
        FinanceBook.Budget budget = new FinanceBook.Budget("b", "food", "2026-10", 500_000); book.save(budget, false);
        book.save(transaction(FinanceBook.Type.EXPENSE, 90_000, "food", cash, null), false);
        book.save(transaction(FinanceBook.Type.EXPENSE, 10_000, "transport", cash, null), false);
        FinanceBook.Transaction otherMonth = transaction(FinanceBook.Type.EXPENSE, 30_000, "food", cash, null);
        otherMonth.occurredAt += 32L * 24 * 60 * 60 * 1000; book.save(otherMonth, false);
        assertEquals(90_000, book.spent(budget));
    }
    @Test public void warningThresholdAndLargePercentagesAreExact() {
        FinanceBook.Budget budget = new FinanceBook.Budget("b", "food", "2026-10", 100_000); book.save(budget, false);
        FinanceBook.Transaction value = transaction(FinanceBook.Type.EXPENSE, 80_000, "food", cash, null); book.save(value, false);
        assertTrue(book.usage(budget).warning); assertFalse(book.usage(budget).exceeded); assertEquals("80", book.usage(budget).percentage);
        FinanceBook.Budget tiny = new FinanceBook.Budget("b", "food", "2026-10", 1); book.save(tiny, true);
        assertEquals("8000000", book.usage(tiny).percentage); assertEquals(100, book.usage(tiny).progress);
    }
    @Test public void duplicateBudgetFailsButSelfEditAndOtherMonthAreAllowed() {
        book.save(new FinanceBook.Budget("b", "food", "2026-10", 100_000), false);
        problem(FinanceBook.Problem.DUPLICATE_BUDGET, () -> book.save(new FinanceBook.Budget("c", "food", "2026-10", 200_000), false));
        book.save(new FinanceBook.Budget("b", "food", "2026-10", 200_000), true);
        book.save(new FinanceBook.Budget("c", "food", "2026-11", 200_000), false);
        assertEquals(200_000, book.budgetLimit("2026-10"));
    }
    @Test public void monthUsesVietnamBoundaryRegardlessOfDeviceZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")); utc.clear(); utc.set(2026, Calendar.OCTOBER, 31, 17, 0);
            assertEquals("2026-11", FinanceBook.month(utc.getTimeInMillis()));
            assertEquals("2026-10", FinanceBook.month(utc.getTimeInMillis() - 1));
        } finally { TimeZone.setDefault(original); }
    }
    @Test public void invalidAmountsAndMalformedCurrencyAreRejected() {
        for (String value : new String[]{"", "0", "-1", "1.5", "1,50", "1e6", "1 000", "1.00.000", "1000000000000"})
            problem(FinanceBook.Problem.AMOUNT, () -> FinanceBook.parseAmount(value));
        assertEquals(95_000, FinanceBook.parseAmount("95.000")); assertEquals(FinanceBook.MAX_AMOUNT, FinanceBook.parseAmount("999.999.999.999"));
        problem(FinanceBook.Problem.AMOUNT, () -> book.save(transaction(FinanceBook.Type.EXPENSE, 0, "food", cash, null), false));
    }
    @Test public void invalidWalletCategoryAndSameWalletAreRejectedWithoutChangingBook() {
        problem(FinanceBook.Problem.WALLET, () -> book.save(transaction(FinanceBook.Type.EXPENSE, 10, "food", "foreign-user", null), false));
        problem(FinanceBook.Problem.CATEGORY, () -> book.save(transaction(FinanceBook.Type.EXPENSE, 10, "salary", cash, null), false));
        problem(FinanceBook.Problem.SAME_WALLET, () -> book.save(transaction(FinanceBook.Type.TRANSFER, 10, null, cash, cash), false));
        problem(FinanceBook.Problem.CATEGORY, () -> book.save(new FinanceBook.Budget("b", "salary", "2026-10", 100), false));
        assertTrue(book.state().transactions.isEmpty());
    }
    @Test public void editAfterDeletionCannotRecreateTheRecord() {
        FinanceBook.Transaction value = transaction(FinanceBook.Type.EXPENSE, 100, "food", cash, null);
        problem(FinanceBook.Problem.NOT_FOUND, () -> book.save(value, true));
        book.save(value, false);
        problem(FinanceBook.Problem.NOT_FOUND, () -> book.save(value, false));
        book.deleteTransaction(value.id); problem(FinanceBook.Problem.NOT_FOUND, () -> book.save(value, true));
    }
    @Test public void searchIgnoresVietnameseAccentsAndMatchesDestinationWallet() {
        book.save(transaction(FinanceBook.Type.TRANSFER, 100, null, cash, bank), false);
        assertEquals(1, book.transactions("2026-10", null, "quan dong", bank, null).size());
        assertEquals(0, book.transactions("2026-10", FinanceBook.Type.EXPENSE, "", null, null).size());
    }
    @Test public void changingBudgetMonthDoesNotChangeLedger() {
        book.save(new FinanceBook.Budget("b", "food", "2026-10", 100_000), false);
        book.save(transaction(FinanceBook.Type.EXPENSE, 50_000, "food", cash, null), false);
        book.save(new FinanceBook.Budget("b", "food", "2026-11", 200_000), true);
        assertEquals(0, book.spent(book.budget("b"))); assertEquals(950_000, book.balance(cash));
    }
    @Test public void namesAndMonthsAreValidated() {
        problem(FinanceBook.Problem.NAME, () -> book.addWallet("tiền mặt", 0));
        problem(FinanceBook.Problem.NAME, () -> book.addCategory(" An uong ", FinanceBook.Type.EXPENSE));
        for (String month : new String[]{"2026-00", "2026-13", "26-10", "2026-1", ""})
            problem(FinanceBook.Problem.DATE, () -> FinanceBook.validateMonth(month));
    }
    @Test public void persistedStateWithDanglingReferencesIsRejected() {
        FinanceBook.State corrupt = FinanceBook.empty();
        corrupt.transactions.add(transaction(FinanceBook.Type.EXPENSE, 100, "food", "missing", null));
        assertThrows(FinanceBook.Failure.class, () -> new FinanceBook(corrupt));
    }
    @Test public void savingsAreSeparateFromAvailableWallets() {
        String savings = book.addWallet("Tiết kiệm", 4_000_000, FinanceBook.WalletType.SAVINGS).id;
        book.save(transaction(FinanceBook.Type.TRANSFER, 100_000, null, cash, savings), false);
        assertEquals(7_000_000, book.assets(false)); assertEquals(4_100_000, book.assets(true));
    }
}
