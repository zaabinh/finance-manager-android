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
import com.example.fintrack.app.budget.remote.BudgetResponse;
import com.example.fintrack.app.transaction.remote.TransactionApiService;
import com.example.fintrack.app.transaction.remote.TransactionResponse;
import com.example.fintrack.app.transaction.remote.TransactionPageResponse;
import com.google.gson.Gson;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Mock is explicit at build time. Server failures never write/fall back to the mock ledger. */
public final class FinanceRepository {
    public static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private final MockFinanceStore mock;
    private final FinanceApiBundle api;
    public FinanceRepository(Context context, String owner) {
        mock = BuildConfig.USE_MOCK ? new MockFinanceStore(context, owner) : null;
        if (BuildConfig.USE_MOCK) api = null;
        else {
            retrofit2.Retrofit base = ApiClient.getClient(context);
            okhttp3.OkHttpClient client = ((okhttp3.OkHttpClient) base.callFactory()).newBuilder().addInterceptor(chain -> {
                com.example.fintrack.app.core.session.SessionManager session = com.example.fintrack.app.core.session.SessionManager.getInstance(context);
                if (!owner.equals(session.getUserId()) || !session.isLoggedIn()
                        || !("Bearer " + session.getAccessToken()).equals(chain.request().header("Authorization")))
                    throw new java.io.IOException("Session changed before financial request");
                return chain.proceed(chain.request());
            }).build();
            retrofit2.Retrofit configured = base.newBuilder().client(client).build();
            WalletApiService w = configured.create(WalletApiService.class);
            CategoryApiService c = configured.create(CategoryApiService.class);
            BudgetApiService b = configured.create(BudgetApiService.class);
            TransactionApiService t = configured.create(TransactionApiService.class);
            api = new FinanceApiBundle(w, c, b, t);
        }
    }
    public FinanceBook read() {
        if (mock != null) return mock.read();
        FinanceBook.State state = new FinanceBook.State();
        for (WalletResponse wallet : FinanceAction.required(api.wallets.wallets())) state.wallets.add(wallet.domain());
        for (CategoryResponse category : FinanceAction.required(api.categories.categories())) state.categories.add(category.domain());
        for (BudgetResponse budget : FinanceAction.required(api.budgets.budgets())) state.budgets.add(budget.domain());
        int page = 0;
        while (true) {
            TransactionPageResponse result = FinanceAction.required(api.transactions.transactions(page, 200));
            if (result.items == null || result.page != page || result.totalPages < 0 || result.totalPages > 10000)
                throw new FinanceBook.Failure(FinanceBook.Problem.SERVER);
            for (TransactionResponse value : result.items) state.transactions.add(value.domain());
            if (++page >= result.totalPages) break;
        }
        try { return new FinanceBook(state); }
        catch (Exception error) { throw new FinanceBook.Failure(FinanceBook.Problem.SERVER); }
    }
    /** Acknowledging the mutation is independent from refreshing, which can fail after a successful save. */
    public FinanceBook mutate(FinanceAction action, FinanceBook previous) {
        if (mock != null) return mock.mutate(action.local);
        if (previous == null) throw new FinanceBook.Failure(FinanceBook.Problem.NETWORK);
        Gson gson = new Gson();
        FinanceBook validation = new FinanceBook(gson.fromJson(gson.toJson(previous.state()), FinanceBook.State.class));
        action.local.accept(validation);
        action.remote.send(api);
        return null;
    }
}
