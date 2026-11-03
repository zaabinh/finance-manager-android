package com.example.fintrack.app.finance.data;

import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.remote.FinanceApiBundle;
import com.example.fintrack.app.transaction.remote.TransactionRequest;
import com.example.fintrack.app.budget.remote.BudgetRequest;
import com.example.fintrack.app.wallet.remote.WalletRequest;
import com.example.fintrack.app.category.remote.CategoryRequest;

import java.io.IOException;
import java.util.function.Consumer;
import retrofit2.Call;
import retrofit2.Response;

/** A typed command shared by the deterministic mock and the real HTTP repository. */
public final class FinanceAction {
    interface Remote { void send(FinanceApiBundle api); }
    final Consumer<FinanceBook> local;
    final Remote remote;
    private FinanceAction(Consumer<FinanceBook> local, Remote remote) { this.local = local; this.remote = remote; }
    public static FinanceAction transaction(FinanceBook.Transaction value, boolean editing) {
        return new FinanceAction(book -> book.save(value, editing), api -> {
            TransactionRequest request = new TransactionRequest(value);
            required(editing ? api.transactions.updateTransaction(value.id, request) : api.transactions.addTransaction(request));
        });
    }
    public static FinanceAction budget(FinanceBook.Budget value, boolean editing) {
        return new FinanceAction(book -> book.save(value, editing), api -> {
            BudgetRequest request = new BudgetRequest(value);
            required(editing ? api.budgets.updateBudget(value.id, request) : api.budgets.addBudget(request));
        });
    }
    public static FinanceAction deleteTransaction(String id) {
        return new FinanceAction(book -> book.deleteTransaction(id), api -> execute(api.transactions.deleteTransaction(id)));
    }
    public static FinanceAction deleteBudget(String id) {
        return new FinanceAction(book -> book.deleteBudget(id), api -> execute(api.budgets.deleteBudget(id)));
    }
    public static FinanceAction wallet(String name, long opening, FinanceBook.WalletType type) {
        return new FinanceAction(book -> book.addWallet(name, opening, type), api -> required(api.wallets.addWallet(new WalletRequest(name, opening, type))));
    }
    public static FinanceAction category(String name, FinanceBook.Type type) {
        return new FinanceAction(book -> book.addCategory(name, type), api -> required(api.categories.addCategory(new CategoryRequest(name, type))));
    }
    public static <T> T required(Call<T> call) {
        T body = execute(call);
        if (body == null) throw new FinanceBook.Failure(FinanceBook.Problem.SERVER);
        return body;
    }
    public static <T> T execute(Call<T> call) {
        try {
            Response<T> response = call.execute();
            if (!response.isSuccessful()) {
                if (response.errorBody() != null) response.errorBody().close();
                int status = response.code();
                throw new FinanceBook.Failure(status == 401 ? FinanceBook.Problem.UNAUTHORIZED
                        : status == 409 ? FinanceBook.Problem.CONFLICT : status == 404 ? FinanceBook.Problem.NOT_FOUND
                        : status == 400 || status == 422 ? FinanceBook.Problem.VALIDATION : FinanceBook.Problem.SERVER);
            }
            return response.body();
        } catch (IOException e) { throw new FinanceBook.Failure(FinanceBook.Problem.NETWORK); }
    }
}
