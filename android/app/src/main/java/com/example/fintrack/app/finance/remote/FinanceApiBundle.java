package com.example.fintrack.app.finance.remote;

import com.example.fintrack.app.wallet.remote.WalletApiService;
import com.example.fintrack.app.category.remote.CategoryApiService;
import com.example.fintrack.app.budget.remote.BudgetApiService;
import com.example.fintrack.app.transaction.remote.TransactionApiService;

/**
 * Lớp gom nhóm (Bundle) các API service của các phân hệ tài chính:
 * Ví (Wallets), Danh mục (Categories), Ngân sách (Budgets) và Giao dịch (Transactions).
 */
public final class FinanceApiBundle {
    public final WalletApiService wallets;
    public final CategoryApiService categories;
    public final BudgetApiService budgets;
    public final TransactionApiService transactions;

    public FinanceApiBundle(WalletApiService w, CategoryApiService c, BudgetApiService b, TransactionApiService t) {
        this.wallets = w;
        this.categories = c;
        this.budgets = b;
        this.transactions = t;
    }
}
