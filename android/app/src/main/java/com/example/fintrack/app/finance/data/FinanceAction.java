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
        return new FinanceAction(book -> { supported(value); book.save(value, editing); }, api -> {
            supported(value);
            TransactionRequest request = new TransactionRequest(value, editing);
            com.example.fintrack.app.transaction.remote.TransactionResponse response = required(editing
                    ? api.transactions.updateTransaction(value.id, request) : api.transactions.addTransaction(request));
            // A POST replay returns the current record; a VOIDED one is not a newly created active transaction.
            if ("VOIDED".equals(response.status)) {
                FinanceBook.Failure failure = new FinanceBook.Failure(FinanceBook.Problem.VOIDED);
                failure.code = editing ? "TRANSACTION_VOIDED" : "TRANSACTION_REPLAY_VOIDED"; throw failure;
            }
        });
    }
    /** TRANSFER is out of scope until the backend supports it (contract section 5). */
    private static void supported(FinanceBook.Transaction value) {
        if (value.type == FinanceBook.Type.TRANSFER) {
            FinanceBook.Failure failure = new FinanceBook.Failure(FinanceBook.Problem.VALIDATION);
            failure.code = "TRANSACTION_TYPE_UNSUPPORTED"; throw failure;
        }
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
        return wallet(name, opening, type, true, 0);
    }
    public static FinanceAction wallet(String name, long opening, FinanceBook.WalletType type, boolean included, int order) {
        return new FinanceAction(book -> {
            FinanceBook.Wallet value = book.addWallet(name, opening, type); value.includeInAssets = included; value.sortOrder = order;
        }, api -> required(api.wallets.addWallet(new WalletRequest(name, opening, type, included, order))));
    }
    public static FinanceAction walletUpdate(String id, String name, long opening, FinanceBook.WalletType type, boolean included, int order) {
        return new FinanceAction(book -> book.updateWallet(id, name, opening, type, included, order),
                api -> required(api.wallets.updateWallet(id, new WalletRequest(name, opening, type, included, order))));
    }
    public static FinanceAction walletStatus(String id, boolean active) {
        return new FinanceAction(book -> book.walletStatus(id, active), api -> required(api.wallets.walletStatus(id,
                new com.example.fintrack.app.wallet.remote.WalletStatusRequest(active))));
    }
    public static FinanceAction walletArchive(String id) {
        return new FinanceAction(book -> book.walletStatus(id, false), api -> execute(api.wallets.archiveWallet(id)));
    }
    public static FinanceAction category(String name, FinanceBook.Type type) {
        return category(name, type, null, null, 0);
    }
    public static FinanceAction category(String name, FinanceBook.Type type, String icon, String color, int order) {
        return new FinanceAction(book -> {
            FinanceBook.Category value = book.addCategory(name, type); value.iconKey = icon; value.colorHex = color; value.sortOrder = order;
        }, api -> required(api.categories.addCategory(new CategoryRequest(name, type, icon, color, order))));
    }
    public static FinanceAction categoryUpdate(String id, String name, FinanceBook.Type type, String icon, String color, int order) {
        return new FinanceAction(book -> book.updateCategory(id, name, type, icon, color, order),
                api -> required(api.categories.updateCategory(id, new CategoryRequest(name, type, icon, color, order))));
    }
    public static FinanceAction categoryStatus(String id, boolean active) {
        return new FinanceAction(book -> book.categoryStatus(id, active), api -> required(api.categories.categoryStatus(id,
                new com.example.fintrack.app.category.remote.CategoryStatusRequest(active))));
    }
    public static FinanceAction categoryArchive(String id) {
        return new FinanceAction(book -> book.categoryStatus(id, false), api -> execute(api.categories.archiveCategory(id)));
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
                int status = response.code();
                FinanceBook.Problem problem = status == 401 ? FinanceBook.Problem.UNAUTHORIZED
                        : status == 409 ? FinanceBook.Problem.CONFLICT : status == 404 ? FinanceBook.Problem.NOT_FOUND
                        : status == 400 || status == 422 ? FinanceBook.Problem.VALIDATION : FinanceBook.Problem.SERVER;
                String message = null, code = null;
                java.util.List<String> fieldNames = new java.util.ArrayList<>();
                if (response.errorBody() != null) {
                    try (okhttp3.ResponseBody body = response.errorBody()) {
                        com.example.fintrack.app.core.network.ApiErrorResponse error = new com.google.gson.Gson().fromJson(
                                body.string(), com.example.fintrack.app.core.network.ApiErrorResponse.class);
                        if (error != null) {
                            code = error.getErrorCode();
                            if ("WALLET_INSUFFICIENT_FUNDS".equals(code)) problem = FinanceBook.Problem.INSUFFICIENT_FUNDS;
                            else if ("TRANSACTION_VOIDED".equals(code)) problem = FinanceBook.Problem.VOIDED;
                            // These have a precise Vietnamese resource; the server message is English.
                            boolean localized = problem == FinanceBook.Problem.INSUFFICIENT_FUNDS || problem == FinanceBook.Problem.VOIDED
                                    || problem == FinanceBook.Problem.NOT_FOUND;
                            message = localized ? null : error.getMessage();
                            if (error.getFieldErrors() != null && !error.getFieldErrors().isEmpty()) {
                                StringBuilder fields = new StringBuilder();
                                for (com.example.fintrack.app.core.network.ApiErrorResponse.FieldError field : error.getFieldErrors()) {
                                    if (fields.length() > 0) fields.append("\n");
                                    fields.append(field.getField()).append(": ").append(field.getMessage());
                                    fieldNames.add(field.getField());
                                }
                                message = fields.toString();
                            }
                        }
                    } catch (com.google.gson.JsonParseException ignored) { }
                }
                FinanceBook.Failure failure = new FinanceBook.Failure(problem, message);
                failure.code = code; failure.fields = fieldNames; throw failure;
            }
            return response.body();
        } catch (IOException e) { throw new FinanceBook.Failure(FinanceBook.Problem.NETWORK); }
    }
}
