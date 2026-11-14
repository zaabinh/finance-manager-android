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
        bank = book.addWallet("Ngân hàng", 2_000_000, WalletType.BANK).id;
        Calendar calendar = Calendar.getInstance(FinanceBook.ZONE); calendar.clear(); calendar.set(2026, Calendar.OCTOBER, 15, 12, 0);
        october = calendar.getTimeInMillis();
    }
    private Transaction transaction(TransactionType type, long amount, String category, String from, String to) {
        return new Transaction(UUID.randomUUID().toString(), type, amount, category, from, to, october, "Cà phê sáng", "Quán Đồng");
    }
    private void problem(FinanceProblem problem, Runnable call) {
        assertEquals(problem, assertThrows(FinanceFailure.class, call::run).problem);
    }
    @Test public void expenseAndIncomeUpdateDerivedBalances() {
        book.save(transaction(TransactionType.EXPENSE, 95_000, "food", cash, null), false);
        book.save(transaction(TransactionType.INCOME, 5_000_000, "salary", bank, null), false);
        assertEquals(905_000, book.balance(cash)); assertEquals(7_000_000, book.balance(bank));
        assertEquals(95_000, book.total(TransactionType.EXPENSE, "2026-10"));
        assertEquals(5_000_000, book.total(TransactionType.INCOME, "2026-10"));
    }
    @Test public void transferPreservesAssetsAndNeverConsumesBudget() {
        Budget budget = new Budget("b", "food", "2026-10", 500_000); book.save(budget, false);
        book.save(transaction(TransactionType.TRANSFER, 400_000, "food", cash, bank), false);
        assertEquals(600_000, book.balance(cash)); assertEquals(2_400_000, book.balance(bank));
        assertEquals(3_000_000, book.assets(false)); assertEquals(0, book.spent(budget));
        assertEquals(0, book.total(TransactionType.EXPENSE, "2026-10")); assertEquals(0, book.total(TransactionType.INCOME, "2026-10"));
    }
    @Test public void editingReversesOldPostingAcrossWalletsAndTypes() {
        Transaction old = transaction(TransactionType.EXPENSE, 80_000, "food", cash, null); book.save(old, false);
        book.save(new Transaction(old.id, TransactionType.INCOME, 120_000, "salary", bank, null, october, "", ""), true);
        assertEquals(1_000_000, book.balance(cash)); assertEquals(2_120_000, book.balance(bank));
        assertEquals(0, book.total(TransactionType.EXPENSE, "2026-10"));
    }
    @Test public void editingTransferReversesBothOldEntries() {
        Transaction old = transaction(TransactionType.TRANSFER, 100_000, null, cash, bank); book.save(old, false);
        book.save(new Transaction(old.id, TransactionType.TRANSFER, 300_000, null, bank, cash, october, "", ""), true);
        assertEquals(1_300_000, book.balance(cash)); assertEquals(1_700_000, book.balance(bank));
        book.deleteTransaction(old.id); assertEquals(3_000_000, book.assets(false)); assertEquals(1_000_000, book.balance(cash));
    }
    @Test public void deleteRecalculatesBudgetAndKeepsOtherTransactions() {
        Budget budget = new Budget("b", "food", "2026-10", 100_000); book.save(budget, false);
        Transaction first = transaction(TransactionType.EXPENSE, 95_000, "food", cash, null);
        book.save(first, false); book.save(transaction(TransactionType.EXPENSE, 15_000, "food", cash, null), false);
        assertTrue(book.usage(budget).exceeded); assertEquals(-10_000, book.usage(budget).remaining);
        book.deleteTransaction(first.id); assertEquals(15_000, book.spent(budget)); assertFalse(book.usage(budget).warning);
        // Soft delete: the voided record stays for audit, budget deletion never touches the ledger.
        book.deleteBudget(budget.id); assertEquals(2, book.snapshot().transactions.size());
        assertEquals("VOIDED", book.snapshot().transactions.get(0).status);
    }
    @Test public void budgetsUseExactCategoryAndMonthAndExpenseOnly() {
        Budget budget = new Budget("b", "food", "2026-10", 500_000); book.save(budget, false);
        book.save(transaction(TransactionType.EXPENSE, 90_000, "food", cash, null), false);
        book.save(transaction(TransactionType.EXPENSE, 10_000, "transport", cash, null), false);
        Transaction otherMonth = transaction(TransactionType.EXPENSE, 30_000, "food", cash, null);
        book.save(new Transaction(otherMonth.id, otherMonth.type, otherMonth.amount, otherMonth.categoryId,
                otherMonth.walletId, otherMonth.destinationWalletId, otherMonth.occurredAt + 32L * 24 * 60 * 60 * 1000,
                otherMonth.note, otherMonth.payee), false);
        assertEquals(90_000, book.spent(budget));
    }
    @Test public void warningThresholdAndLargePercentagesAreExact() {
        Budget budget = new Budget("b", "food", "2026-10", 100_000); book.save(budget, false);
        Transaction value = transaction(TransactionType.EXPENSE, 80_000, "food", cash, null); book.save(value, false);
        assertTrue(book.usage(budget).warning); assertFalse(book.usage(budget).exceeded); assertEquals("80", book.usage(budget).percentage);
        Budget tiny = new Budget("b", "food", "2026-10", 1); book.save(tiny, true);
        assertEquals("8000000", book.usage(tiny).percentage); assertEquals(100, book.usage(tiny).progress);
    }
    @Test public void duplicateBudgetFailsButSelfEditAndOtherMonthAreAllowed() {
        book.save(new Budget("b", "food", "2026-10", 100_000), false);
        problem(FinanceProblem.DUPLICATE_BUDGET, () -> book.save(new Budget("c", "food", "2026-10", 200_000), false));
        book.save(new Budget("b", "food", "2026-10", 200_000), true);
        book.save(new Budget("c", "food", "2026-11", 200_000), false);
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
            problem(FinanceProblem.AMOUNT, () -> FinanceBook.parseAmount(value));
        assertEquals(95_000, FinanceBook.parseAmount("95.000")); assertEquals(FinanceBook.MAX_AMOUNT, FinanceBook.parseAmount("999.999.999.999"));
        problem(FinanceProblem.AMOUNT, () -> book.save(transaction(TransactionType.EXPENSE, 0, "food", cash, null), false));
    }
    @Test public void invalidWalletCategoryAndSameWalletAreRejectedWithoutChangingBook() {
        problem(FinanceProblem.WALLET, () -> book.save(transaction(TransactionType.EXPENSE, 10, "food", "foreign-user", null), false));
        problem(FinanceProblem.CATEGORY, () -> book.save(transaction(TransactionType.EXPENSE, 10, "salary", cash, null), false));
        problem(FinanceProblem.SAME_WALLET, () -> book.save(transaction(TransactionType.TRANSFER, 10, null, cash, cash), false));
        problem(FinanceProblem.CATEGORY, () -> book.save(new Budget("b", "salary", "2026-10", 100), false));
        assertTrue(book.snapshot().transactions.isEmpty());
    }
    @Test public void editAfterDeletionCannotRecreateTheRecord() {
        Transaction value = transaction(TransactionType.EXPENSE, 100, "food", cash, null);
        problem(FinanceProblem.NOT_FOUND, () -> book.save(value, true));
        book.save(value, false);
        problem(FinanceProblem.NOT_FOUND, () -> book.save(value, false));
        book.deleteTransaction(value.id); problem(FinanceProblem.VOIDED, () -> book.save(value, true));
    }
    @Test public void searchIgnoresVietnameseAccentsAndMatchesDestinationWallet() {
        book.save(transaction(TransactionType.TRANSFER, 100, null, cash, bank), false);
        assertEquals(1, book.transactions("2026-10", null, "quan dong", bank, null).size());
        assertEquals(0, book.transactions("2026-10", TransactionType.EXPENSE, "", null, null).size());
    }
    @Test public void changingBudgetMonthDoesNotChangeLedger() {
        book.save(new Budget("b", "food", "2026-10", 100_000), false);
        book.save(transaction(TransactionType.EXPENSE, 50_000, "food", cash, null), false);
        book.save(new Budget("b", "food", "2026-11", 200_000), true);
        assertEquals(0, book.spent(book.budget("b"))); assertEquals(950_000, book.balance(cash));
    }
    @Test public void namesAndMonthsAreValidated() {
        problem(FinanceProblem.NAME, () -> book.addWallet("tiền mặt", 0));
        // Contract: case-insensitive but accent-sensitive.
        problem(FinanceProblem.NAME, () -> book.addCategory(" ĂN UỐNG ", TransactionType.EXPENSE));
        book.addCategory("An uong", TransactionType.EXPENSE);
        for (String month : new String[]{"2026-00", "2026-13", "26-10", "2026-1", ""})
            problem(FinanceProblem.DATE, () -> FinanceBook.validateMonth(month));
    }
    @Test public void persistedStateWithDanglingReferencesIsRejected() {
        FinanceSnapshot.Builder corrupt = FinanceBook.empty();
        corrupt.transactions.add(transaction(TransactionType.EXPENSE, 100, "food", "missing", null));
        assertThrows(FinanceFailure.class, () -> new FinanceBook(corrupt));
    }
    @Test public void transferToArchivedDestinationIsRejectedAndVoidChecksDestination() {
        String savings = book.addWallet("Tiết kiệm", 0, WalletType.SAVINGS).id;
        book.walletStatus(savings, false);
        problem(FinanceProblem.ARCHIVED, () -> book.save(transaction(TransactionType.TRANSFER, 1, null, cash, savings), false));
        Transaction moved = transaction(TransactionType.TRANSFER, 300_000, null, cash, bank); book.save(moved, false);
        book.save(transaction(TransactionType.EXPENSE, 2_100_000, "food", bank, null), false); // bank 2_300_000 -> 200_000
        problem(FinanceProblem.INSUFFICIENT_FUNDS, () -> book.deleteTransaction(moved.id));
        assertEquals("POSTED", moved.status);
        problem(FinanceProblem.INSUFFICIENT_FUNDS, () -> book.save(transaction(TransactionType.TRANSFER, 700_001, null, cash, bank), false));
    }
    @Test public void queryWalletFilterMatchesTransferSourceOrDestination() {
        book.save(transaction(TransactionType.TRANSFER, 100, null, cash, bank), false);
        book.save(transaction(TransactionType.EXPENSE, 100, "food", cash, null), false);
        assertEquals(1, book.query(new FinanceBook.Query(null, null, null, bank, null, null, null), 0, 50).items.size());
        assertEquals(2, book.query(new FinanceBook.Query(null, null, null, cash, null, null, null), 0, 50).items.size());
        assertEquals(1, book.query(new FinanceBook.Query(null, null, TransactionType.TRANSFER, null, null, null, null), 0, 50).items.size());
        assertEquals(100, book.total(TransactionType.EXPENSE, "2026-10"));
        assertEquals(0, book.total(TransactionType.INCOME, "2026-10"));
    }
    @Test public void savingsAreSeparateFromAvailableWallets() {
        String savings = book.addWallet("Tiết kiệm", 4_000_000, WalletType.SAVINGS).id;
        book.save(transaction(TransactionType.TRANSFER, 100_000, null, cash, savings), false);
        assertEquals(7_000_000, book.assets(false)); assertEquals(4_100_000, book.assets(true));
    }
}
