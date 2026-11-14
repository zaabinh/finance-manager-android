package com.example.fintrack.app.finance.ui;

import com.example.fintrack.app.core.di.AppExecutors;
import com.example.fintrack.app.core.network.RequestHandle;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.finance.data.*;
import com.example.fintrack.app.finance.domain.FinanceBook;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** Delivers even cancelled calls so tests also exercise generation guards. */
public final class DeferredFinanceRepository extends FinanceRepository {
    public static final class Pending<T> implements RequestHandle {
        public final Consumer<Resource<T>> callback;
        public boolean cancelled;
        Pending(Consumer<Resource<T>> callback) { this.callback = callback; }
        @Override public void cancel() { cancelled = true; }
        public void deliver(Resource<T> result) { callback.accept(result); }
    }
    public final List<Pending<FinanceBook>> reads = new ArrayList<>();
    public final List<Pending<FinanceBook.Page>> pages = new ArrayList<>();
    public final List<Pending<MutationReceipt>> writes = new ArrayList<>();
    public final List<Integer> requestedPages = new ArrayList<>();
    public Scope scope;
    public String detailId;
    public DeferredFinanceRepository() { super((MockFinanceStore) null); }
    @Override public RequestHandle readAsync(Scope scope, String id, AppExecutors executors, Consumer<Resource<FinanceBook>> callback) {
        this.scope = scope; detailId = id;
        Pending<FinanceBook> pending = new Pending<>(callback); reads.add(pending); return pending;
    }
    @Override public RequestHandle transactionsAsync(FinanceBook.Query query, int page, AppExecutors executors, Consumer<Resource<FinanceBook.Page>> callback) {
        requestedPages.add(page);
        Pending<FinanceBook.Page> pending = new Pending<>(callback); pages.add(pending); return pending;
    }
    @Override public RequestHandle mutateAsync(FinanceAction action, FinanceBook previous, BooleanSupplier canStart, AppExecutors executors, Consumer<Resource<MutationReceipt>> callback) {
        Pending<MutationReceipt> pending = new Pending<>(callback); writes.add(pending); return pending;
    }
}
