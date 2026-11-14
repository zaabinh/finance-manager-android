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
        session.login("a", "r", true, true);
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
        session.clear(session.current()); session.login("b", "r", true, true); page(0, 0, 1, "old");
        assertTrue(model.rows().isEmpty()); model.refresh(); assertEquals(1, repository.pages.size());
    }
    @Test public void sessionEndedDuringReadStopsLoadingAndSignalsExpiryOnceWithoutData() {
        TransactionListViewModel model = model(); model.query(query(null)); page(0, 0, 2, "first"); model.more();
        java.util.List<Resource<java.util.List<Transaction>>> states = new java.util.ArrayList<>();
        model.state().observeForever(states::add);
        states.clear();
        session.clear(session.current());
        page(1, 1, 2, "second");
        page(1, 1, 2, "second");
        assertFalse(model.initialLoading()); assertFalse(model.pageLoading());
        assertEquals(1, states.size());
        Resource<java.util.List<Transaction>> state = states.get(0);
        assertEquals(Resource.Status.ERROR, state.getStatus()); assertNull(state.getData());
        assertEquals(com.example.fintrack.app.core.error.AppError.SESSION_EXPIRED, state.getError().code);
        assertEquals(1, model.rows().size());
    }
    @Test public void sessionReplacedByNewLoginNeverDeliversOldData() {
        TransactionListViewModel model = model(); model.query(query(null));
        session.clear(session.current()); session.login("b", "r", true, true); page(0, 0, 1, "old");
        assertTrue(model.rows().isEmpty()); assertNull(model.state().getValue().getData());
        assertEquals(com.example.fintrack.app.core.error.AppError.SESSION_EXPIRED, model.state().getValue().getError().code);
    }
    @Test public void readThatCannotStartAfterSessionEndsSignalsExpiry() {
        java.util.ArrayDeque<Runnable> queue = new java.util.ArrayDeque<>();
        AppExecutors queued = new AppExecutors(queue::add, queue::add, Runnable::run, Runnable::run);
        session.login("a", "r", true, true);
        TransactionListViewModel model = new TransactionListViewModel(session, origin -> repository, queued);
        model.query(query(null)); session.clear(session.current()); queue.remove().run();
        assertTrue(repository.pages.isEmpty()); assertFalse(model.initialLoading());
        assertEquals(com.example.fintrack.app.core.error.AppError.SESSION_EXPIRED, model.state().getValue().getError().code);
    }
    @Test public void clearedModelStaysSilentAfterSessionEnds() {
        TransactionListViewModel model = model(); ViewModelStore owner = new ViewModelStore();
        new ViewModelProvider(owner, new ViewModelProvider.Factory() {
            @Override public <T extends ViewModel> T create(Class<T> type) { return type.cast(model); }
        }).get(TransactionListViewModel.class);
        model.query(query(null)); owner.clear(); session.clear(session.current());
        page(0, 0, 1, "late");
        assertEquals(Resource.Status.LOADING, model.state().getValue().getStatus());
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
