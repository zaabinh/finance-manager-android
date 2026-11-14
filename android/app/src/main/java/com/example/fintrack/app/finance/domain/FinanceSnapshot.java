package com.example.fintrack.app.finance.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Detached immutable state. Builders are inputs, never a view of a live FinanceBook. */
public final class FinanceSnapshot {
    public final int version;
    public final String timeZone;
    public final boolean serverSnapshot;
    public final List<Wallet> wallets;
    public final List<Category> categories;
    public final List<Transaction> transactions;
    public final List<Budget> budgets;
    public final List<MonthlyBudget> monthlyBudgets;
    public FinanceSnapshot(int version, String timeZone, boolean serverSnapshot, List<Wallet> wallets, List<Category> categories, List<Transaction> transactions, List<Budget> budgets, List<MonthlyBudget> monthlyBudgets) {
        this.version=version; this.timeZone=timeZone; this.serverSnapshot=serverSnapshot;
        this.wallets=copy(wallets);
        this.categories=copy(categories);
        this.transactions=copy(transactions);
        this.budgets=copy(budgets);
        this.monthlyBudgets=copy(monthlyBudgets == null ? Collections.emptyList() : monthlyBudgets);
    }
    private static <T> List<T> copy(List<T> values) {
        if (values == null) throw new FinanceFailure(FinanceProblem.STORAGE);
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
    public static final class Builder {
        public int version=1;
        public String timeZone="Asia/Ho_Chi_Minh";
        public boolean serverSnapshot;
        public List<Wallet> wallets=new ArrayList<>();
        public List<Category> categories=new ArrayList<>();
        public List<Transaction> transactions=new ArrayList<>();
        public List<Budget> budgets=new ArrayList<>();
        public List<MonthlyBudget> monthlyBudgets=new ArrayList<>();
        public FinanceSnapshot build() { return new FinanceSnapshot(version,timeZone,serverSnapshot,wallets, categories, transactions, budgets, monthlyBudgets); }
    }
}
