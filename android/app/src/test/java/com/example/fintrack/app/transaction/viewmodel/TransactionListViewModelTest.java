package com.example.fintrack.app.transaction.viewmodel;

import static org.junit.Assert.*;
import androidx.lifecycle.*;
import com.example.fintrack.app.core.di.AppExecutors;
import com.example.fintrack.app.core.network.HttpErrorParser;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.SessionStore;
import com.example.fintrack.app.finance.domain.*;
import com.example.fintrack.app.finance.ui.DeferredFinanceRepository;
import com.example.fintrack.app.testing.*;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;

public class TransactionListViewModelTest {
    private static final AppExecutors DIRECT = new AppExecutors(Runnable::run, Runnable::run, Runnable::run, Runnable::run);
    @Rule public final InstantLiveData liveData = new InstantLiveData();
    private final SessionStore session = new SessionStore(new FakeSessionStorage());
    private final DeferredFinanceRepository repository = new DeferredFinanceRepository();
    private TransactionListViewModel model() {
        session.login("a", "r", true);
        return new TransactionListViewModel(session, origin -> repository, DIRECT);
    }
    private FinanceBook.Query query(String search) { return FinanceBook.Query.month("2026-11", null, null, null, search); }
    private Transaction row(String id) { return new Transaction(id, TransactionType.EXPENSE, 1, "category", "wallet", null, 0, "", ""); }
    private void page(int call, int index, int total, String... ids) {
        java.util.List<Transaction> rows = new java.util.ArrayList<>();
        for (String id : ids) rows.add(row(id));
        repository.pages.get(call).deliver(Resource.success(new FinanceBook.Page(rows, index, total)));
    }
    @Test public void newerFilterCancelsReadAndIgnoresOldSuccessAndFailure() {
        TransactionListViewModel model = model(); model.query(query("old")); model.query(query("new"));
        assertTrue(repository.pages.get(0).cancelled); assertTrue(model.initialLoading());
        page(1, 0, 1, "new");
        page(0, 0, 3, "old");
        repository.pages.get(0).deliver(Resource.error(HttpErrorParser.from(new java.io.IOException()), null));
        assertEquals("new", model.rows().get(0).id); assertEquals(1, model.totalPages());
        assertEquals(Resource.Status.SUCCESS, model.state().getValue().getStatus()); assertNull(model.loadMoreError().getValue());
    }
    @Test public void failedPageRetainsRowsAndRetriesSamePageWithoutDuplicates() {
        TransactionListViewModel model = model(); model.query(query(null)); page(0, 0, 2, "first");
        model.more(); assertTrue(model.pageLoading()); assertFalse(model.initialLoading());
        repository.pages.get(1).deliver(Resource.error(HttpErrorParser.from(new java.io.IOException()), null));
        assertEquals(0, model.page()); assertEquals(1, model.rows().size()); assertTrue(model.hasMore());
        assertNotNull(model.loadMoreError().getValue()); assertFalse(model.pageLoading());
        model.more(); page(2, 1, 2, "first", "second");
        assertEquals(Arrays.asList(0, 1, 1), repository.requestedPages);
        assertEquals(1, model.page()); assertEquals(2, model.rows().size()); assertFalse(model.hasMore());
        assertNull(model.loadMoreError().getValue());
    }
    @Test public void queryChangeDuringPaginationDropsOldAppend() {
        TransactionListViewModel model = model(); model.query(query("old")); page(0, 0, 2, "old"); model.more();
        model.query(query("new")); assertTrue(repository.pages.get(1).cancelled);
        page(2, 0, 1, "new"); page(1, 1, 2, "old-second");
        assertEquals(1, model.rows().size()); assertEquals("new", model.rows().get(0).id); assertEquals(0, model.page());
    }
    @Test public void initialFailureCanRetryPageZeroAndIdenticalFilterDoesNotDuplicateCall() {
        TransactionListViewModel model = model(); model.query(query("same")); model.query(query("same"));
        assertEquals(1, repository.pages.size());
        repository.pages.get(0).deliver(Resource.error(HttpErrorParser.from(new java.io.IOException()), null));
        assertEquals(-1, model.page()); assertEquals(Resource.Status.ERROR, model.state().getValue().getStatus());
        model.refresh(); page(1, 0, 1, "retry");
        assertEquals(Arrays.asList(0, 0), repository.requestedPages); assertEquals("retry", model.rows().get(0).id);
    }
    @Test public void reloginSuppressesOldResultEvenForSameAccount() {
        TransactionListViewModel model = model(); model.query(query(null));
        session.clear(session.current()); session.login("b", "r", true); page(0, 0, 1, "old");
        assertTrue(model.rows().isEmpty()); model.refresh(); assertEquals(1, repository.pages.size());
    }
    @Test public void clearingOwnerCancelsItsReadAndSuppressesLateResult() {
        TransactionListViewModel model = model(); ViewModelStore owner = new ViewModelStore();
        new ViewModelProvider(owner, new ViewModelProvider.Factory() {
            @Override public <T extends ViewModel> T create(Class<T> type) { return type.cast(model); }
        }).get(TransactionListViewModel.class);
        model.query(query(null)); owner.clear(); assertTrue(repository.pages.get(0).cancelled);
        page(0, 0, 1, "late"); assertTrue(model.rows().isEmpty());
    }
}
