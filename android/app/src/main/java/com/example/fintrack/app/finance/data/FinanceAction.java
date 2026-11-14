package com.example.fintrack.app.finance.data;

import com.example.fintrack.app.core.error.AppFailure;

import com.example.fintrack.app.core.error.AppError;

import com.example.fintrack.app.core.network.HttpErrorParser;

import com.example.fintrack.app.finance.domain.Budget;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.FinanceFailure;
import com.example.fintrack.app.finance.domain.FinanceProblem;
import com.example.fintrack.app.finance.domain.Transaction;
import com.example.fintrack.app.finance.domain.TransactionType;
import com.example.fintrack.app.finance.domain.WalletType;

import com.example.fintrack.app.finance.remote.FinanceApiBundle;
import com.example.fintrack.app.transaction.remote.TransactionRequest;
import com.example.fintrack.app.budget.remote.BudgetRequest;
import com.example.fintrack.app.wallet.remote.WalletRequest;
import com.example.fintrack.app.category.remote.CategoryRequest;

import java.io.IOException;
import java.util.function.Function;
import retrofit2.Call;
import retrofit2.Response;

/** A typed command shared by the deterministic mock and the real HTTP repository. */
public final class FinanceAction {
    interface Remote { String send(FinanceApiBundle api); }
    final Remote remote;
    final Function<FinanceBook, String> localResult;
    private FinanceAction(Function<FinanceBook, String> local, Remote remote) {
        this.localResult = local; this.remote = remote;
    }
    public static FinanceAction transaction(Transaction value, boolean editing) {
        return new FinanceAction(book -> { book.save(value, editing); return editing ? null : value.id; }, api -> {
            TransactionRequest request = new TransactionRequest(value, editing);
            com.example.fintrack.app.transaction.remote.TransactionResponse response = required(editing
                    ? api.transactions.updateTransaction(value.id, request) : api.transactions.addTransaction(request));
            // A POST replay returns the current record; a VOIDED one is not a newly created active transaction.
            if ("VOIDED".equals(response.status)) {
                throw new FinanceFailure(FinanceProblem.VOIDED, editing ? "TRANSACTION_VOIDED" : "TRANSACTION_REPLAY_VOIDED",
                        java.util.Collections.emptyList(),null);
            }
            return editing ? null : response.transactionId;
        });
    }
    /** Category budget under the monthly budget of its month (mock resolves the parent and applies the balance block). */
    public static FinanceAction budget(Budget value, boolean editing) {
        return new FinanceAction(book -> { book.saveCategoryBudget(value, editing); return editing ? null : value.id; }, api -> {
            BudgetRequest request = new BudgetRequest(value);
            String id = required(editing ? api.budgets.updateBudget(value.id, request) : api.budgets.addBudget(request)).budgetId;
            return editing ? null : id;
        });
    }
    /** POST /monthly-budgets: month and its category budgets in one atomic request. */
    public static FinanceAction monthlyBudget(String id, String month, java.util.List<Budget> categories) {
        return new FinanceAction(book -> { book.createMonthlyBudget(id, month, categories); return id; },
                api -> required(api.budgets.addMonthlyBudget(new com.example.fintrack.app.budget.remote.MonthlyBudgetRequest(month, categories))).monthlyBudgetId);
    }
    /** DELETE /monthly-budgets/{id}: archives its category budgets too; rejected for the current month. */
    public static FinanceAction deleteMonthlyBudget(String id) {
        return new FinanceAction(book -> { book.deleteMonthlyBudget(id, System.currentTimeMillis()); return null; },
                api -> { execute(api.budgets.deleteMonthlyBudget(id)); return null; });
    }
    public static FinanceAction deleteTransaction(String id) {
        return new FinanceAction(book -> { book.deleteTransaction(id); return null; }, api -> { execute(api.transactions.deleteTransaction(id)); return null; });
    }
    public static FinanceAction deleteBudget(String id) {
        return new FinanceAction(book -> { book.deleteBudget(id); return null; }, api -> { execute(api.budgets.deleteBudget(id)); return null; });
    }
    public static FinanceAction wallet(String name, long opening, WalletType type) {
        return wallet(name, opening, type, true, 0);
    }
    public static FinanceAction wallet(String name, long opening, WalletType type, boolean included, int order) {
        return new FinanceAction(book -> book.addWallet(name, opening, type, included, order).id,
                api -> required(api.wallets.addWallet(new WalletRequest(name, opening, type, included, order))).walletId);
    }
    public static FinanceAction walletUpdate(String id, String name, long opening, WalletType type, boolean included, int order) {
        return new FinanceAction(book -> { book.updateWallet(id, name, opening, type, included, order); return null; },
                api -> { required(api.wallets.updateWallet(id, new WalletRequest(name, opening, type, included, order))); return null; });
    }
    public static FinanceAction walletStatus(String id, boolean active) {
        return new FinanceAction(book -> { book.walletStatus(id, active); return null; }, api -> { required(api.wallets.walletStatus(id,
                new com.example.fintrack.app.wallet.remote.WalletStatusRequest(active))); return null; });
    }
    public static FinanceAction walletArchive(String id) {
        return new FinanceAction(book -> { book.walletStatus(id, false); return null; }, api -> { execute(api.wallets.archiveWallet(id)); return null; });
    }
    public static FinanceAction category(String name, TransactionType type) {
        return category(name, type, null, null, 0);
    }
    public static FinanceAction category(String name, TransactionType type, String icon, String color, int order) {
        return new FinanceAction(book -> book.addCategory(name, type, icon, color, order).id,
                api -> required(api.categories.addCategory(new CategoryRequest(name, type, icon, color, order))).categoryId);
    }
    public static FinanceAction categoryUpdate(String id, String name, TransactionType type, String icon, String color, int order) {
        return new FinanceAction(book -> { book.updateCategory(id, name, type, icon, color, order); return null; },
                api -> { required(api.categories.updateCategory(id, new CategoryRequest(name, type, icon, color, order))); return null; });
    }
    public static FinanceAction categoryStatus(String id, boolean active) {
        return new FinanceAction(book -> { book.categoryStatus(id, active); return null; }, api -> { required(api.categories.categoryStatus(id,
                new com.example.fintrack.app.category.remote.CategoryStatusRequest(active))); return null; });
    }
    public static FinanceAction categoryArchive(String id) {
        return new FinanceAction(book -> { book.categoryStatus(id, false); return null; }, api -> { execute(api.categories.archiveCategory(id)); return null; });
    }
    public static <T> T required(Call<T> call) {
        T body = execute(call);
        if (body == null) throw new AppFailure(
                AppError.local(AppError.Category.UNEXPECTED,
                        "LOCAL_MISSING_DATA", null));
        return body;
    }
    public static <T> T execute(Call<T> call) {
        try {
            Response<T> response = call.execute();
            if (!response.isSuccessful()) throw new AppFailure(
                    HttpErrorParser.parse(response));
            return response.body();
        } catch (IOException error) { throw new AppFailure(
                HttpErrorParser.from(error)); }
    }
}
