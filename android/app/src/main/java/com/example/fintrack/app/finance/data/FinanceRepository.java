package com.example.fintrack.app.finance.data;

import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.FinanceFailure;
import com.example.fintrack.app.finance.domain.FinanceProblem;
import com.example.fintrack.app.finance.domain.FinanceSnapshot;
import com.example.fintrack.app.finance.domain.Transaction;

import com.example.fintrack.app.finance.remote.FinanceApiBundle;
import com.example.fintrack.app.wallet.remote.WalletResponse;
import com.example.fintrack.app.category.remote.CategoryResponse;
import com.example.fintrack.app.transaction.remote.TransactionResponse;
import com.example.fintrack.app.transaction.remote.TransactionPageResponse;
import com.example.fintrack.app.core.di.AppExecutors;
import com.example.fintrack.app.core.error.AppError;
import com.example.fintrack.app.core.error.AppFailure;
import com.example.fintrack.app.core.network.HttpErrorParser;
import com.example.fintrack.app.core.network.RequestHandle;
import com.example.fintrack.app.core.network.Resource;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import retrofit2.Call;

/** Mock is explicit at build time. Server failures never write/fall back to the mock ledger. */
public class FinanceRepository {
    public enum Scope { ALL, WALLETS, CATEGORIES, REFERENCES, BUDGETS, TRANSACTION_DETAIL }
    public static final int PAGE_SIZE = 50;
    private final MockFinanceStore mock;
    private final FinanceApiBundle api;
    private final com.example.fintrack.app.user.remote.UserApiService users;
    private volatile String cachedTimeZone;
    /** Server-backed repository. The services must come from a client guarded for one account (see AppContainer). */
    public FinanceRepository(FinanceApiBundle api, com.example.fintrack.app.user.remote.UserApiService users) {
        this.mock = null; this.api = api; this.users = users;
    }
    /** Mock-flavor repository backed by one account's local file. */
    public FinanceRepository(MockFinanceStore mock) {
        this.mock = mock; this.api = null; this.users = null;
    }
    public FinanceBook read() { return read(Scope.ALL, null); }
    public FinanceBook read(Scope scope, String detailId) {
        return read(scope, detailId, new ReadRequest());
    }
    private FinanceBook read(Scope scope, String detailId, ReadRequest request) {
        if (mock != null) return mock.read();
        FinanceSnapshot.Builder state = new FinanceSnapshot.Builder();
        state.serverSnapshot = true;
        String tz = cachedTimeZone;
        if (tz == null && users != null) {
            com.example.fintrack.app.user.remote.UserProfileResponse profile = request.required(users.getProfile()).getData();
            if (profile != null && profile.getTimeZone() != null) {
                tz = profile.getTimeZone();
                cachedTimeZone = tz;
            }
        }
        if (tz == null) tz = java.util.TimeZone.getDefault().getID();
        state.timeZone = tz;
        if (scope != Scope.CATEGORIES)
            for (WalletResponse wallet : request.required(api.wallets.wallets())) state.wallets.add(wallet.domain());
        if (scope != Scope.WALLETS)
            for (CategoryResponse category : request.required(api.categories.categories())) state.categories.add(category.domain());
        if (scope == Scope.ALL || scope == Scope.BUDGETS) {
            // Budgets are read as monthly budgets with their category budgets; detailId is a monthlyBudgetId.
            java.util.List<com.example.fintrack.app.budget.remote.MonthlyBudgetResponse> monthly = detailId == null
                    ? request.required(api.budgets.monthlyBudgets(null, "ACTIVE"))
                    : java.util.Collections.singletonList(request.required(api.budgets.monthlyBudget(detailId)));
            for (com.example.fintrack.app.budget.remote.MonthlyBudgetResponse item : monthly) {
                if (item == null) throw new FinanceFailure(FinanceProblem.SERVER);
                item.into(state);
            }
        }
        if (scope == Scope.TRANSACTION_DETAIL && detailId != null) {
            state.transactions.add(request.required(api.transactions.transaction(detailId)).domain());
        }
        try { return new FinanceBook(state); }
        catch (Exception error) { throw new FinanceFailure(FinanceProblem.SERVER, error); }
    }
    /** History page filtered/sorted by the server; mock serves the same query from its full ledger. */
    public FinanceBook.Page transactions(FinanceBook.Query query, int page) {
        return transactions(query, page, new ReadRequest());
    }
    private FinanceBook.Page transactions(FinanceBook.Query query, int page, ReadRequest request) {
        if (mock != null) return mock.read().query(query, page, PAGE_SIZE);
        TransactionPageResponse body = request.required(api.transactions.transactions(page, PAGE_SIZE, query.dateFrom, query.dateTo,
                query.walletId, query.categoryId, query.type, query.q, query.sort));
        if (body.items == null || body.totalPages < 0) throw new FinanceFailure(FinanceProblem.SERVER);
        java.util.List<Transaction> items = new java.util.ArrayList<>();
        for (TransactionResponse item : body.items) {
            if (item == null || item.transactionId == null || item.transactionType == null) throw new FinanceFailure(FinanceProblem.SERVER);
            items.add(item.domain());
        }
        return new FinanceBook.Page(items, page, body.totalPages);
    }
    /** Acknowledging the mutation is independent from refreshing, which can fail after a successful save. */
    public MutationReceipt mutate(FinanceAction action, FinanceBook previous) {
        if (mock != null) {
            String[] created = new String[1];
            FinanceBook book = mock.mutate(candidate -> created[0] = action.localResult.apply(candidate));
            return new MutationReceipt(created[0], book);
        }
        if (previous == null) throw new FinanceFailure(FinanceProblem.NETWORK);
        // A scoped server snapshot is not a complete ledger. The server validates the command atomically.
        return new MutationReceipt(action.remote.send(api), null);
    }

    public RequestHandle readAsync(Scope scope, String detailId, AppExecutors executors,
                                   Consumer<Resource<FinanceBook>> callback) {
        return submit(executors, request -> read(scope, detailId, request), callback);
    }
    public RequestHandle transactionsAsync(FinanceBook.Query query, int page, AppExecutors executors,
                                           Consumer<Resource<FinanceBook.Page>> callback) {
        return submit(executors, request -> transactions(query, page, request), callback);
    }
    public RequestHandle mutateAsync(FinanceAction action, FinanceBook previous, BooleanSupplier canStart, AppExecutors executors,
                                     Consumer<Resource<MutationReceipt>> callback) {
        // Writes keep running after the caller is cleared: cancellation only suppresses their delivery.
        return submit(executors, request -> {
            if (!canStart.getAsBoolean()) throw new FinanceFailure(FinanceProblem.UNAUTHORIZED);
            return mutate(action, previous);
        }, callback);
    }
    private <T> RequestHandle submit(AppExecutors executors, Function<ReadRequest, T> work,
                                      Consumer<Resource<T>> callback) {
        ReadRequest request = new ReadRequest();
        Executor worker = mock == null ? executors.io() : executors.serial();
        worker.execute(() -> {
            if (request.canceled) return;
            Resource<T> result;
            try { result = Resource.success(work.apply(request)); }
            catch (Exception failure) { result = Resource.error(error(failure), null); }
            Resource<T> terminal = result;
            executors.main().execute(() -> request.deliver(terminal, callback));
        });
        return request;
    }
    public static AppError error(Throwable failure) {
        if (failure instanceof AppFailure) return ((AppFailure) failure).error;
        if (failure instanceof FinanceFailure) {
            FinanceFailure finance = (FinanceFailure) failure;
            java.util.List<AppError.FieldError> fields = new ArrayList<>();
            for (String field : finance.fields) fields.add(new AppError.FieldError(field, null));
            return new AppError(finance.problem == FinanceProblem.STORAGE ? AppError.Category.STORAGE : AppError.Category.DOMAIN,
                    null, finance.code, fields, finance.shortfallMinor, finance);
        }
        return HttpErrorParser.from(failure);
    }
    private static final class ReadRequest implements RequestHandle {
        private volatile boolean canceled;
        private final java.util.Set<Call<?>> activeCalls = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
        <T> T required(Call<T> call) {
            boolean stopped;
            synchronized (this) {
                stopped = canceled;
                if (!stopped) activeCalls.add(call);
            }
            if (stopped) { call.cancel(); throw new java.util.concurrent.CancellationException(); }
            try { return FinanceAction.required(call); }
            finally { synchronized (this) { activeCalls.remove(call); } }
        }
        synchronized <T> void deliver(Resource<T> result, Consumer<Resource<T>> callback) {
            if (!canceled) callback.accept(result);
        }
        @Override public void cancel() {
            java.util.List<Call<?>> calls;
            synchronized (this) {
                if (canceled) return;
                canceled = true;
                calls = new java.util.ArrayList<>(activeCalls);
                activeCalls.clear();
            }
            for (Call<?> call : calls) {
                if (call != null) call.cancel();
            }
        }
    }
}
