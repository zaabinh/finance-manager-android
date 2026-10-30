package com.example.fintrack.app.finance.data;

import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.remote.FinanceApiService;
import com.example.fintrack.app.finance.remote.FinanceDtos;
import java.io.IOException;
import java.util.function.Consumer;
import retrofit2.Call;
import retrofit2.Response;

/** A typed command shared by the deterministic mock and the real HTTP repository. */
public final class FinanceAction {
    interface Remote { void send(FinanceApiService api); }
    final Consumer<FinanceBook> local;
    final Remote remote;
    private FinanceAction(Consumer<FinanceBook> local, Remote remote) { this.local = local; this.remote = remote; }
    public static FinanceAction transaction(FinanceBook.Transaction value, boolean editing) {
        return new FinanceAction(book -> book.save(value, editing), api -> {
            FinanceDtos.TransactionRequest request = new FinanceDtos.TransactionRequest(value);
            required(editing ? api.updateTransaction(value.id, request) : api.addTransaction(request));
        });
    }
    public static FinanceAction budget(FinanceBook.Budget value, boolean editing) {
        return new FinanceAction(book -> book.save(value, editing), api -> {
            FinanceDtos.BudgetRequest request = new FinanceDtos.BudgetRequest(value);
            required(editing ? api.updateBudget(value.id, request) : api.addBudget(request));
        });
    }
    public static FinanceAction deleteTransaction(String id) {
        return new FinanceAction(book -> book.deleteTransaction(id), api -> execute(api.deleteTransaction(id)));
    }
    public static FinanceAction deleteBudget(String id) {
        return new FinanceAction(book -> book.deleteBudget(id), api -> execute(api.deleteBudget(id)));
    }
    public static FinanceAction wallet(String name, long opening, FinanceBook.WalletType type) {
        return new FinanceAction(book -> book.addWallet(name, opening, type), api -> required(api.addWallet(new FinanceDtos.WalletRequest(name, opening, type))));
    }
    public static FinanceAction category(String name, FinanceBook.Type type) {
        return new FinanceAction(book -> book.addCategory(name, type), api -> required(api.addCategory(new FinanceDtos.CategoryRequest(name, type))));
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
