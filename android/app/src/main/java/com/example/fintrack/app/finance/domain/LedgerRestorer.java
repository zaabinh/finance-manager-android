package com.example.fintrack.app.finance.domain;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;

/** Validates persisted facts without owning or changing the ledger. */
final class LedgerRestorer {
    private LedgerRestorer() { }

    static String validate(FinanceSnapshot state) {
        if (state == null || state.version != 1 || state.wallets == null || state.categories == null
                || state.transactions == null || state.budgets == null) throw new FinanceFailure(FinanceProblem.STORAGE);
        String timeZone = state.timeZone == null ? "Asia/Ho_Chi_Minh" : state.timeZone;
        // TimeZone silently falls back to GMT for unknown IDs.
        if (!Arrays.asList(TimeZone.getAvailableIDs()).contains(timeZone)) throw new FinanceFailure(FinanceProblem.STORAGE);
        Set<String> walletIds = new HashSet<>(), categoryIds = new HashSet<>();
        for (Wallet wallet : state.wallets) {
            if (wallet == null || wallet.id == null || wallet.id.isEmpty() || !walletIds.add(wallet.id)
                    || wallet.walletType == null || wallet.openingBalance < 0 || wallet.openingBalance > FinanceBook.MAX_AMOUNT)
                throw new FinanceFailure(FinanceProblem.STORAGE);
            FinanceBook.validName(wallet.name);
        }
        for (Category category : state.categories) {
            if (category == null || category.id == null || category.id.isEmpty() || !categoryIds.add(category.id)
                    || category.type == null || category.type == TransactionType.TRANSFER) throw new FinanceFailure(FinanceProblem.STORAGE);
            FinanceBook.validName(category.name);
        }
        Set<String> monthlyIds = new HashSet<>(), activeMonths = new HashSet<>();
        if (state.monthlyBudgets != null) for (MonthlyBudget monthly : state.monthlyBudgets) {
            if (monthly == null || monthly.id == null || monthly.id.isEmpty() || !monthlyIds.add(monthly.id)
                    || (monthly.archivedAt == null && !activeMonths.add(String.valueOf(monthly.month)))) throw new FinanceFailure(FinanceProblem.STORAGE);
            BudgetPolicy.validateMonth(monthly.month);
        }
        return timeZone;
    }

}
