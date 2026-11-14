package com.example.fintrack.app.finance.domain;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/** Characterization of public ledger behavior before policy extraction. */
public class FinanceBookRefactorContractTest {
    private static final long OCTOBER = 1_792_000_000_000L;

    private static FinanceSnapshot.Builder state() {
        FinanceSnapshot.Builder state = FinanceBook.empty();
        state.wallets.add(new Wallet("cash", "Cash", 100));
        state.wallets.add(new Wallet("bank", "Bank", 0));
        return state;
    }

    private static Transaction tx(String id, TransactionType type, long amount, String category,
                                  String wallet, String destination, long at) {
        return new Transaction(id, type, amount, category, wallet, destination, at, "", "");
    }

    private static FinanceFailure problem(FinanceProblem expected, Runnable action) {
        FinanceFailure failure = assertThrows(FinanceFailure.class, action::run);
        assertEquals(expected, failure.problem);
        return failure;
    }

    private static void unchanged(FinanceSnapshot before, FinanceBook book) {
        FinanceSnapshot after = book.snapshot();
        assertEquals(before.version, after.version);
        assertEquals(before.timeZone, after.timeZone);
        assertEquals(before.serverSnapshot, after.serverSnapshot);
        assertEquals(before.wallets, after.wallets);
        assertEquals(before.categories, after.categories);
        assertEquals(before.transactions, after.transactions);
        assertEquals(before.budgets, after.budgets);
        assertEquals(before.monthlyBudgets, after.monthlyBudgets);
    }

    @Test public void transactionErrorsKeepValidationPrecedenceBeforeEditLookup() {
        FinanceBook book = new FinanceBook(state());
        problem(FinanceProblem.NOT_FOUND, () -> book.save(tx(null, null, 0, null, null, null, -1), true));
        problem(FinanceProblem.AMOUNT, () -> book.save(tx("missing", TransactionType.EXPENSE, 0, null, null, null, -1), true));
        problem(FinanceProblem.DATE, () -> book.save(tx("missing", TransactionType.EXPENSE, 1, null, null, null, -1), true));
        problem(FinanceProblem.WALLET, () -> book.save(tx("missing", TransactionType.EXPENSE, 1, "missing", "missing", null, OCTOBER), true));
        problem(FinanceProblem.CATEGORY, () -> book.save(tx("missing", TransactionType.EXPENSE, 1, "salary", "cash", null, OCTOBER), true));
        book.walletStatus("cash", false);
        problem(FinanceProblem.ARCHIVED, () -> book.save(tx("missing", TransactionType.TRANSFER, 1, null, "cash", "missing", OCTOBER), true));
        assertTrue(book.transactions().isEmpty());
    }

    @Test public void restoreNormalizesLegacyFieldsAndPreservesAuditMetadata() {
        FinanceSnapshot.Builder state = state();
        state.transactions.add(new Transaction("move", TransactionType.TRANSFER, 10, "legacy-category", "cash", "bank",
                OCTOBER, "  note  ", null, "VOIDED", "old-void-time", "request-1"));
        state.transactions.add(new Transaction("income", TransactionType.INCOME, 5, "salary", "cash", "legacy-destination",
                OCTOBER, null, "  payee  ", "POSTED", null, "request-2"));
        FinanceBook book = new FinanceBook(state);
        Transaction move = book.transaction("move");
        assertNull(move.categoryId);
        assertEquals("bank", move.destinationWalletId);
        assertEquals("note", move.note);
        assertEquals("", move.payee);
        assertEquals("VOIDED", move.status);
        assertEquals("old-void-time", move.voidedAt);
        assertEquals("request-1", move.clientRequestId);
        Transaction income = book.transaction("income");
        assertNull(income.destinationWalletId);
        assertEquals("", income.note);
        assertEquals("payee", income.payee);
        assertEquals("request-2", income.clientRequestId);
        assertEquals(105, book.balance("cash"));
        assertEquals(0, book.balance("bank"));
    }

    @Test public void restoreAllowsArchivedReferencesButNewTransactionsAndEditsRejectThem() {
        FinanceSnapshot.Builder state = state();
        state.wallets.set(0, state.wallets.get(0).withArchivedAt("archived-wallet"));
        state.categories.set(0, state.categories.get(0).withArchivedAt("archived-category"));
        state.transactions.add(tx("old", TransactionType.EXPENSE, 10, "food", "cash", null, OCTOBER));
        FinanceBook book = new FinanceBook(state);
        FinanceSnapshot before = book.snapshot();
        problem(FinanceProblem.ARCHIVED, () -> book.save(tx("new", TransactionType.EXPENSE, 1, "food", "cash", null, OCTOBER), false));
        problem(FinanceProblem.ARCHIVED, () -> book.save(book.transaction("old"), true));
        unchanged(before, book);
        book.walletStatus("cash", true);
        before = book.snapshot();
        problem(FinanceProblem.ARCHIVED, () -> book.save(tx("new", TransactionType.EXPENSE, 1, "food", "cash", null, OCTOBER), false));
        unchanged(before, book);
    }

    @Test public void rejectedUpdatesPreserveEntireSnapshot() {
        FinanceBook book = new FinanceBook(state());
        book.save(tx("expense", TransactionType.EXPENSE, 80, "food", "cash", null, OCTOBER), false);
        FinanceSnapshot before = book.snapshot();
        problem(FinanceProblem.INSUFFICIENT_FUNDS, () -> book.save(tx("expense", TransactionType.EXPENSE, 101, "food", "cash", null, OCTOBER), true));
        unchanged(before, book);
        problem(FinanceProblem.CATEGORY, () -> book.save(tx("expense", TransactionType.EXPENSE, 1, "salary", "cash", null, OCTOBER), true));
        unchanged(before, book);
        problem(FinanceProblem.INSUFFICIENT_FUNDS, () -> book.updateWallet("cash", "Changed", 79, WalletType.BANK, false, 2));
        unchanged(before, book);
        problem(FinanceProblem.CONFLICT, () -> book.updateCategory("food", "Changed", TransactionType.INCOME, null, null, 0));
        unchanged(before, book);
    }

    @Test public void transferVoidRequiresDestinationFundsAndIsIdempotentAtExactBoundary() {
        FinanceBook book = new FinanceBook(state());
        book.save(tx("move", TransactionType.TRANSFER, 100, null, "cash", "bank", OCTOBER), false);
        book.save(tx("spend", TransactionType.EXPENSE, 1, "food", "bank", null, OCTOBER), false);
        FinanceSnapshot before = book.snapshot();
        problem(FinanceProblem.INSUFFICIENT_FUNDS, () -> book.deleteTransaction("move"));
        unchanged(before, book);
        book.deleteTransaction("spend");
        book.deleteTransaction("move");
        assertEquals(100, book.balance("cash"));
        assertEquals(0, book.balance("bank"));
        before = book.snapshot();
        book.deleteTransaction("move");
        unchanged(before, book);
        problem(FinanceProblem.VOIDED, () -> book.save(tx("move", TransactionType.TRANSFER, 1, null, "cash", "bank", OCTOBER), true));
        unchanged(before, book);
    }

    @Test public void crossParentBudgetEditRollsBackAndArchivedChildrenStayWithTheirLifecycle() {
        FinanceBook book = new FinanceBook(state());
        MonthlyBudget october = book.createMonthlyBudget("oct", "2026-10", Collections.singletonList(new Budget("food-budget", "food", "2026-10", 40)));
        MonthlyBudget november = book.createMonthlyBudget("nov", "2026-11", Collections.singletonList(new Budget("bills-budget", "bills", "2026-11", 80)));
        Budget old = book.budget("food-budget");
        FinanceFailure failure = problem(FinanceProblem.CONFLICT, () -> book.saveCategoryBudget(new Budget(old.id, "food", "2026-11", 30), true));
        assertEquals(FinanceBook.BUDGET_EXCEEDS_AVAILABLE_BALANCE, failure.code);
        assertEquals(Long.valueOf(10), failure.shortfallMinor);
        assertSame(old, book.budget(old.id));
        assertEquals(40, book.monthlyTotal(october));
        assertEquals(80, book.monthlyTotal(november));
        book.deleteMonthlyBudget("oct", 1_795_000_000_000L);
        MonthlyBudget archived = book.monthlyBudget("oct");
        book.createMonthlyBudget("replacement", "2026-10", Collections.singletonList(new Budget("replacement-food", "food", "2026-10", 10)));
        assertEquals(Collections.singletonList(book.budget(old.id)), book.categoryBudgets(archived));
        assertEquals(archived.archivedAt, book.budget(old.id).archivedAt);
        assertEquals(40, book.monthlyTotal(archived));
        problem(FinanceProblem.ARCHIVED, () -> book.save(old, true));
    }

    @Test public void partialMonthlyCreationFailureRemovesOnlyNewParentAndChildren() {
        FinanceBook book = new FinanceBook(state());
        book.save(new Budget("existing", "transport", "2026-09", 1), false);
        FinanceSnapshot before = book.snapshot();
        problem(FinanceProblem.NOT_FOUND, () -> book.createMonthlyBudget("oct", "2026-10", Arrays.asList(
                new Budget("new", "food", "2026-10", 1), new Budget("existing", "bills", "2026-10", 1))));
        unchanged(before, book);
    }

    @Test public void zeroServerAggregatesOverrideDerivedValuesWhileNullUsesLedger() {
        FinanceSnapshot.Builder state = state();
        state.serverSnapshot = true;
        state.timeZone = "America/Los_Angeles";
        state.wallets.set(1, new Wallet("bank", "Bank", 20, WalletType.BANK, null, true, 0, 0L));
        state.transactions.add(tx("expense", TransactionType.EXPENSE, 10, "food", "cash", null, OCTOBER));
        MonthlyBudget monthly = new MonthlyBudget("oct", "2026-10", null, 0L, 0L, 80);
        state.monthlyBudgets.add(monthly);
        state.budgets.add(new Budget("food-budget", "food", "2026-10", 40, 0L, 80, null, "oct"));
        FinanceBook book = new FinanceBook(state);
        assertEquals(90, book.balance("cash"));
        assertEquals(0, book.balance("bank"));
        assertEquals(0, book.spent(book.budget("food-budget")));
        assertEquals(10, book.spent(new Budget("derived", "food", "2026-10", 40)));
        assertEquals(0, book.monthlyTotal(monthly));
        assertEquals(0, book.monthlySpent(monthly));
        assertEquals(40, book.monthlyTotal(new MonthlyBudget("oct", "2026-10")));
        assertEquals(10, book.monthlySpent(new MonthlyBudget("oct", "2026-10")));
        assertEquals("2026-10", book.monthAt(1_793_484_000_000L));
        assertEquals("2026-11", FinanceBook.month(1_793_484_000_000L));
    }

    @Test public void serverAggregateOverflowStaysAnArithmeticFailure() {
        FinanceSnapshot.Builder state = state();
        state.wallets.set(0, new Wallet("cash", "Cash", 100, WalletType.CASH, null, true, 0, Long.MAX_VALUE));
        state.wallets.set(1, new Wallet("bank", "Bank", 0, WalletType.BANK, null, true, 0, 1L));
        FinanceBook book = new FinanceBook(state);
        FinanceSnapshot before = book.snapshot();
        assertThrows(ArithmeticException.class, () -> book.assets(false));
        assertThrows(ArithmeticException.class, book::spendable);
        unchanged(before, book);
    }

    @Test public void budgetReductionSkipsOverflowingSpendableAggregate() {
        FinanceSnapshot.Builder state = state();
        state.wallets.set(0, new Wallet("cash", "Cash", 100, WalletType.CASH, null, true, 0, Long.MAX_VALUE));
        state.wallets.set(1, new Wallet("bank", "Bank", 0, WalletType.BANK, null, true, 0, 1L));
        state.monthlyBudgets.add(new MonthlyBudget("oct", "2026-10"));
        state.budgets.add(new Budget("food-budget", "food", "2026-10", 40).withMonthlyBudgetId("oct"));
        FinanceBook book = new FinanceBook(state);
        FinanceSnapshot before = book.snapshot();
        assertThrows(ArithmeticException.class, book::spendable);

        book.saveCategoryBudget(new Budget("food-budget", "food", "2026-10", 20), true);

        assertEquals(20, book.budget("food-budget").limit);
        assertEquals("oct", book.budget("food-budget").monthlyBudgetId);
        assertEquals(20, book.monthlyTotal(book.monthlyBudget("oct")));
        FinanceSnapshot after = book.snapshot();
        assertEquals(1, after.budgets.size());
        assertEquals(before.wallets, after.wallets);
        assertEquals(before.categories, after.categories);
        assertEquals(before.transactions, after.transactions);
        assertEquals(before.monthlyBudgets, after.monthlyBudgets);
        assertEquals(before.timeZone, after.timeZone);
        assertEquals(before.serverSnapshot, after.serverSnapshot);
        assertEquals(before.version, after.version);
    }
}
