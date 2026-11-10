package com.example.fintrack.app.finance.data;

import android.content.Context;
import com.example.fintrack.BuildConfig;
import com.example.fintrack.app.core.network.ApiClient;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.remote.FinanceApiBundle;
import com.example.fintrack.app.wallet.remote.WalletApiService;
import com.example.fintrack.app.wallet.remote.WalletResponse;
import com.example.fintrack.app.category.remote.CategoryApiService;
import com.example.fintrack.app.category.remote.CategoryResponse;
import com.example.fintrack.app.budget.remote.BudgetApiService;
import com.example.fintrack.app.transaction.remote.TransactionApiService;
import com.example.fintrack.app.transaction.remote.TransactionResponse;
import com.example.fintrack.app.transaction.remote.TransactionPageResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Mock is explicit at build time. Server failures never write/fall back to the mock ledger. */
public final class FinanceRepository {
    public enum Scope { ALL, WALLETS, CATEGORIES, REFERENCES, BUDGETS, TRANSACTION_DETAIL }
    public static final ExecutorService IO = Executors.newSingleThreadExecutor();
    public static final int PAGE_SIZE = 50;
    private final MockFinanceStore mock;
    private final FinanceApiBundle api;
    private final com.example.fintrack.app.user.remote.UserApiService users;
    public FinanceRepository(Context context, String owner) {
        mock = BuildConfig.USE_MOCK ? new MockFinanceStore(context, owner) : null;
        if (BuildConfig.USE_MOCK) { api = null; users = null; }
        else {
            retrofit2.Retrofit configured = client(context, owner);
            WalletApiService w = configured.create(WalletApiService.class);
            CategoryApiService c = configured.create(CategoryApiService.class);
            BudgetApiService b = configured.create(BudgetApiService.class);
            TransactionApiService t = configured.create(TransactionApiService.class);
            api = new FinanceApiBundle(w, c, b, t);
            users = configured.create(com.example.fintrack.app.user.remote.UserApiService.class);
        }
    }
    public static retrofit2.Retrofit client(Context context, String owner) {
        retrofit2.Retrofit base = ApiClient.getClient(context);
        okhttp3.OkHttpClient guarded = ((okhttp3.OkHttpClient) base.callFactory()).newBuilder().addInterceptor(chain -> {
            com.example.fintrack.app.core.session.SessionManager session = com.example.fintrack.app.core.session.SessionManager.getInstance(context);
            if (owner == null || !owner.equals(session.getUserId()) || !session.isLoggedIn()
                    || !("Bearer " + session.getAccessToken()).equals(chain.request().header("Authorization")))
                throw new java.io.IOException("Session changed before financial request");
            return chain.proceed(chain.request());
        }).build();
        return base.newBuilder().client(guarded).build();
    }
    public FinanceBook read() { return read(Scope.ALL, null); }
    public FinanceBook read(Scope scope, String detailId) {
        if (mock != null) return mock.read();
        FinanceBook.State state = new FinanceBook.State();
        state.serverSnapshot = true;
        com.example.fintrack.app.user.remote.UserProfileResponse profile = FinanceAction.required(users.getProfile()).getData();
        if (profile == null || profile.getTimeZone() == null) throw new FinanceBook.Failure(FinanceBook.Problem.SERVER);
        state.timeZone = profile.getTimeZone();
        if (scope != Scope.CATEGORIES)
            for (WalletResponse wallet : FinanceAction.required(api.wallets.wallets())) state.wallets.add(wallet.domain());
        if (scope != Scope.WALLETS)
            for (CategoryResponse category : FinanceAction.required(api.categories.categories())) state.categories.add(category.domain());
        if (scope == Scope.ALL || scope == Scope.BUDGETS) {
            // Budgets are read as monthly budgets with their category budgets; detailId is a monthlyBudgetId.
            java.util.List<com.example.fintrack.app.budget.remote.MonthlyBudgetResponse> monthly = detailId == null
                    ? FinanceAction.required(api.budgets.monthlyBudgets(null, "ACTIVE"))
                    : java.util.Collections.singletonList(FinanceAction.required(api.budgets.monthlyBudget(detailId)));
            for (com.example.fintrack.app.budget.remote.MonthlyBudgetResponse item : monthly) {
                if (item == null) throw new FinanceBook.Failure(FinanceBook.Problem.SERVER);
                item.into(state);
            }
        }
        if (scope == Scope.TRANSACTION_DETAIL && detailId != null) {
            state.transactions.add(FinanceAction.required(api.transactions.transaction(detailId)).domain());
        }
        try { return new FinanceBook(state); }
        catch (Exception error) { throw new FinanceBook.Failure(FinanceBook.Problem.SERVER); }
    }
    /** History page filtered/sorted by the server; mock serves the same query from its full ledger. */
    public FinanceBook.Page transactions(FinanceBook.Query query, int page) {
        if (mock != null) return mock.read().query(query, page, PAGE_SIZE);
        TransactionPageResponse body = FinanceAction.required(api.transactions.transactions(page, PAGE_SIZE, query.dateFrom, query.dateTo,
                query.walletId, query.categoryId, query.type, query.q, query.sort));
        if (body.items == null || body.totalPages < 0) throw new FinanceBook.Failure(FinanceBook.Problem.SERVER);
        java.util.List<FinanceBook.Transaction> items = new java.util.ArrayList<>();
        for (TransactionResponse item : body.items) {
            if (item == null || item.transactionId == null || item.transactionType == null) throw new FinanceBook.Failure(FinanceBook.Problem.SERVER);
            items.add(item.domain());
        }
        return new FinanceBook.Page(items, page, body.totalPages);
    }
    /** Acknowledging the mutation is independent from refreshing, which can fail after a successful save. */
    public FinanceBook mutate(FinanceAction action, FinanceBook previous) {
        if (mock != null) return mock.mutate(action.local);
        if (previous == null) throw new FinanceBook.Failure(FinanceBook.Problem.NETWORK);
        // A scoped server snapshot is not a complete ledger. The server validates the command atomically.
        action.remote.send(api);
        return null;
    }
}
