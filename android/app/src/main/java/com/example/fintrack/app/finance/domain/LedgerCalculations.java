package com.example.fintrack.app.finance.domain;

/** Extracted domain rules; no Android or persistence dependencies. */
final class LedgerCalculations {
    private LedgerCalculations() { }
    static long balance(FinanceBook book, FinanceSnapshot.Builder state, String walletId) {
        Wallet wallet = book.wallet(walletId);
        if (wallet == null) throw new FinanceFailure(FinanceProblem.WALLET);
        if (wallet.serverBalanceMinor != null) return wallet.serverBalanceMinor;
        long result = wallet.openingBalance;
        for (Transaction item : state.transactions) {
            if ("VOIDED".equals(item.status)) continue;
            if (walletId.equals(item.walletId)) result = Math.addExact(result, item.type == TransactionType.INCOME ? item.amount : -item.amount);
            if (item.type == TransactionType.TRANSFER && walletId.equals(item.destinationWalletId)) result = Math.addExact(result, item.amount);
        }
        return result;
    }
    static long total(FinanceBook book, FinanceSnapshot.Builder state, TransactionType type, String month) {
        long total = 0;
        for (Transaction item : book.transactions(month, type, "", null, null)) total = Math.addExact(total, item.amount);
        return total;
    }
    static long assets(FinanceBook book, FinanceSnapshot.Builder state, boolean savingsOnly) {
        long result = 0;
        for (Wallet wallet : state.wallets) if (wallet.archivedAt == null && wallet.includeInAssets && (!savingsOnly || wallet.walletType == WalletType.SAVINGS))
            result = Math.addExact(result, book.balance(wallet.id));
        return result;
    }
    static long spent(FinanceBook book, FinanceSnapshot.Builder state, Budget budget) {
        if (budget.serverSpentMinor != null) return budget.serverSpentMinor;
        long total = 0;
        for (Transaction item : book.transactions(budget.month, TransactionType.EXPENSE, "", null, budget.categoryId)) total = Math.addExact(total, item.amount);
        return total;
    }
    static long budgetLimit(FinanceBook book, FinanceSnapshot.Builder state, String month) {
        long result = 0; for (Budget item : book.budgets(month)) result = Math.addExact(result, item.limit); return result;
    }
    static long budgetSpent(FinanceBook book, FinanceSnapshot.Builder state, String month) {
        long result = 0; for (Budget item : book.budgets(month)) result = Math.addExact(result, book.spent(item)); return result;
    }
    static long monthlyTotal(FinanceBook book, FinanceSnapshot.Builder state, MonthlyBudget monthly) {
        if (monthly.serverTotalMinor != null) return monthly.serverTotalMinor;
        long result = 0;
        for (Budget item : book.categoryBudgets(monthly)) result = Math.addExact(result, item.limit);
        return result;
    }
    static long monthlySpent(FinanceBook book, FinanceSnapshot.Builder state, MonthlyBudget monthly) {
        if (monthly.serverSpentMinor != null) return monthly.serverSpentMinor;
        return book.total(TransactionType.EXPENSE, monthly.month);
    }
    static long spendable(FinanceBook book, FinanceSnapshot.Builder state) {
        long result = 0;
        for (Wallet wallet : state.wallets) if (wallet.archivedAt == null && wallet.includeInAssets && wallet.walletType != WalletType.SAVINGS)
            result = Math.addExact(result, book.balance(wallet.id));
        return result;
    }
}
