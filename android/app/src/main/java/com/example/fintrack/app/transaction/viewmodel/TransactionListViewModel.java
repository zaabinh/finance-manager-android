package com.example.fintrack.app.transaction.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.fintrack.app.core.di.AppExecutors;
import com.example.fintrack.app.core.error.AppError;
import com.example.fintrack.app.core.network.RequestHandle;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.SessionSnapshot;
import com.example.fintrack.app.core.session.SessionStore;
import com.example.fintrack.app.finance.data.FinanceRepository;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.Transaction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/** Owns one history query; a newer generation always wins over an older response. */
public final class TransactionListViewModel extends ViewModel {
    private final MutableLiveData<Resource<List<Transaction>>> state = new MutableLiveData<>();
    private final MutableLiveData<AppError> loadMoreError = new MutableLiveData<>();
    private final SessionStore session;
    private final Function<SessionSnapshot, FinanceRepository> repositories;
    private final AppExecutors executors;
    private final SessionSnapshot origin;
    private FinanceBook.Query query;
    private List<Transaction> rows = Collections.emptyList();
    private int page = -1, totalPages;
    private long generation;
    private boolean initialLoading, pageLoading;
    private volatile boolean cleared;
    private RequestHandle read = RequestHandle.NONE;

    public TransactionListViewModel(SessionStore session, Function<SessionSnapshot, FinanceRepository> repositories,
                                    AppExecutors executors) {
        this.session = session; this.repositories = repositories; this.executors = executors;
        origin = session.current();
    }
    public LiveData<Resource<List<Transaction>>> state() { return state; }
    public LiveData<AppError> loadMoreError() { return loadMoreError; }
    public FinanceBook.Query query() { return query; }
    public List<Transaction> rows() { return rows; }
    public int page() { return page; }
    public int totalPages() { return totalPages; }
    public boolean hasMore() { return page + 1 < totalPages; }
    public boolean initialLoading() { return initialLoading; }
    public boolean pageLoading() { return pageLoading; }
    private boolean active() { return !cleared && session.isCurrent(origin); }

    public void query(FinanceBook.Query value) {
        if (!active() || java.util.Objects.equals(query, value)) return;
        query = java.util.Objects.requireNonNull(value);
        rows = Collections.emptyList(); page = -1; totalPages = 0;
        refresh();
    }
    public void refresh() {
        if (!active() || query == null) return;
        read.cancel();
        load(0, ++generation);
    }
    public void more() {
        if (!active() || query == null || initialLoading || pageLoading || page < 0 || !hasMore()) return;
        load(page + 1, generation);
    }
    private void load(int requestedPage, long requested) {
        FinanceBook.Query requestedQuery = query;
        initialLoading = requestedPage == 0;
        pageLoading = requestedPage > 0;
        loadMoreError.setValue(null);
        state.setValue(Resource.loading(rows));
        executors.serial().execute(() -> {
            if (!active()) return;
            try {
                FinanceRepository repository = repositories.apply(origin);
                executors.main().execute(() -> {
                    if (!active() || requested != generation) return;
                    read = repository.transactionsAsync(requestedQuery, requestedPage, executors, result -> {
                        if (!active() || requested != generation) return;
                        initialLoading = false; pageLoading = false;
                        if (result.getStatus() == Resource.Status.ERROR) {
                            failure(result.getError(), requestedPage); return;
                        }
                        FinanceBook.Page next = result.getData();
                        rows = Collections.unmodifiableList(new ArrayList<>(requestedPage == 0
                                ? next.items : FinanceBook.merge(rows, next.items)));
                        page = requestedPage;
                        totalPages = next.totalPages;
                        state.setValue(Resource.success(rows));
                    });
                });
            } catch (Exception error) {
                AppError failure = FinanceRepository.error(error);
                executors.main().execute(() -> {
                    if (!active() || requested != generation) return;
                    initialLoading = false; pageLoading = false;
                    failure(failure, requestedPage);
                });
            }
        });
    }
    private void failure(AppError error, int requestedPage) {
        if (requestedPage == 0) state.setValue(Resource.error(error, rows));
        else {
            state.setValue(Resource.success(rows));
            loadMoreError.setValue(error);
        }
    }
    @Override protected void onCleared() {
        cleared = true;
        ++generation;
        read.cancel();
    }
}
