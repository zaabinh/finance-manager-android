package com.example.fintrack.app.finance.ui;

import static org.junit.Assert.*;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStore;
import com.example.fintrack.app.core.di.AppExecutors;
import com.example.fintrack.app.core.network.HttpErrorParser;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.*;
import com.example.fintrack.app.core.util.Event;
import com.example.fintrack.app.finance.data.*;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.testing.*;
import org.junit.Rule;
import org.junit.Test;

public class FinanceViewModelTest {
    private static final AppExecutors DIRECT = new AppExecutors(Runnable::run, Runnable::run, Runnable::run, Runnable::run);
    @Rule public final InstantLiveData liveData = new InstantLiveData();
    private final SessionStore session = new SessionStore(new FakeSessionStorage());
    private final DeferredFinanceRepository repository = new DeferredFinanceRepository();
    private FinanceViewModel model() {
        session.login("a", "r", true, true);
        return new FinanceViewModel(session, origin -> repository, DIRECT, FinanceRepository.Scope.TRANSACTION_DETAIL, "transaction-42");
    }
    private FinanceBook book() { return new FinanceBook(FinanceBook.empty()); }

    @Test public void constructorScopeAndDetailAreUsedAndTokenRefreshKeepsSession() {
        FinanceViewModel model = model();
        session.updateTokens(session.current(), "new-access", "new-refresh");
        model.refresh();
        assertTrue(model.snapshotLoading()); assertFalse(model.saveLoading());
        assertEquals(FinanceRepository.Scope.TRANSACTION_DETAIL, repository.scope);
        assertEquals("transaction-42", repository.detailId);
        FinanceBook book = book(); repository.reads.get(0).deliver(Resource.success(book));
        assertSame(book, model.state().getValue().getData()); assertFalse(model.snapshotLoading());
    }
    @Test public void replacementReadCancelsAndIgnoresItsPredecessor() {
        FinanceViewModel model = model(); model.refresh(); model.refresh();
        assertTrue(repository.reads.get(0).cancelled);
        FinanceBook current = book(); repository.reads.get(1).deliver(Resource.success(current));
        repository.reads.get(0).deliver(Resource.success(book()));
        assertSame(current, model.state().getValue().getData());
    }
    @Test public void confirmedSaveEmitsItsIdOnceEvenWhenRefreshFails() {
        FinanceViewModel model = model(); model.refresh();
        FinanceBook previous = book(); repository.reads.get(0).deliver(Resource.success(previous));
        model.change(FinanceAction.deleteTransaction("id"), false);
        assertTrue(model.saveLoading());
        repository.writes.get(0).deliver(Resource.success(new MutationReceipt("created-42", null)));
        Event<FinanceViewModel.Command> event = model.command().getValue();
        FinanceViewModel.Command command = event.getContentIfNotHandled();
        assertNotNull(command); assertNull(command.error); assertEquals("created-42", command.receipt.createdEntityId);
        assertFalse(model.saveLoading()); assertNull(event.getContentIfNotHandled());
        repository.reads.get(1).deliver(Resource.error(HttpErrorParser.from(new java.io.IOException()), previous));
        assertEquals(Resource.Status.ERROR, model.state().getValue().getStatus());
        assertSame(event, model.command().getValue()); assertNull(event.getContentIfNotHandled());
        assertEquals(1, repository.writes.size());
    }
    @Test public void saveFailureDoesNotAutomaticallyRetryAndRetainsData() {
        FinanceViewModel model = model(); model.refresh();
        FinanceBook previous = book(); repository.reads.get(0).deliver(Resource.success(previous));
        model.change(FinanceAction.deleteTransaction("id"), true);
        repository.writes.get(0).deliver(Resource.error(HttpErrorParser.from(new java.io.IOException()), null));
        assertEquals(1, repository.writes.size()); assertSame(previous, model.state().getValue().getData());
        assertNotNull(model.command().getValue().getContentIfNotHandled().error); assertFalse(model.saveLoading());
    }
    @Test public void sameAccountReloginSuppressesOldSaveAndRefresh() {
        FinanceViewModel model = model(); model.change(FinanceAction.deleteTransaction("id"), false);
        session.clear(session.current()); session.login("b", "r", true, true);
        repository.writes.get(0).deliver(Resource.success(new MutationReceipt("old", null)));
        assertNull(model.command().getValue()); assertTrue(repository.reads.isEmpty());
        model.refresh(); assertTrue(repository.reads.isEmpty());
    }
    private static int expiries(java.util.List<Resource<FinanceBook>> states) {
        int count = 0;
        for (Resource<FinanceBook> state : states) {
            if (state.getStatus() == Resource.Status.ERROR) {
                assertEquals(com.example.fintrack.app.core.error.AppError.SESSION_EXPIRED, state.getError().code);
                assertEquals(com.example.fintrack.app.finance.domain.FinanceProblem.UNAUTHORIZED, FinanceErrors.problem(state.getError()));
                count++;
            }
        }
        return count;
    }
    private java.util.List<Resource<FinanceBook>> record(FinanceViewModel model) {
        java.util.List<Resource<FinanceBook>> states = new java.util.ArrayList<>();
        model.state().observeForever(states::add);
        return states;
    }
    @Test public void sessionEndedDuringReadStopsLoadingAndSignalsExpiryOnceWithoutData() {
        FinanceViewModel model = model(); model.refresh(); model.refresh();
        java.util.List<Resource<FinanceBook>> states = record(model);
        session.clear(session.current());
        repository.reads.get(1).deliver(Resource.success(book()));
        repository.reads.get(0).deliver(Resource.success(book()));
        assertFalse(model.snapshotLoading());
        assertEquals(1, expiries(states));
        for (Resource<FinanceBook> state : states) assertNull(state.getData());
    }
    @Test public void sessionReplacedByNewLoginNeverDeliversOldData() {
        FinanceViewModel model = model(); model.refresh();
        session.clear(session.current()); session.login("b", "r", true, true);
        repository.reads.get(0).deliver(Resource.success(book()));
        assertNull(model.state().getValue().getData());
        assertEquals(com.example.fintrack.app.core.error.AppError.SESSION_EXPIRED, model.state().getValue().getError().code);
    }
    @Test public void sessionEndedDuringSaveStopsSavingAndSignalsExpiry() {
        FinanceViewModel model = model(); model.change(FinanceAction.deleteTransaction("id"), true);
        session.clear(session.current());
        repository.writes.get(0).deliver(Resource.success(new MutationReceipt("old", null)));
        assertFalse(model.saveLoading()); assertEquals(Boolean.FALSE, model.saveLoadingState().getValue());
        assertNull(model.command().getValue());
        assertEquals(com.example.fintrack.app.core.error.AppError.SESSION_EXPIRED, model.state().getValue().getError().code);
    }
    @Test public void readThatCannotStartAfterSessionEndsSignalsExpiry() {
        java.util.ArrayDeque<Runnable> queue = new java.util.ArrayDeque<>();
        AppExecutors queued = new AppExecutors(queue::add, queue::add, Runnable::run, Runnable::run);
        session.login("a", "r", true, true);
        FinanceViewModel model = new FinanceViewModel(session, origin -> repository, queued, FinanceRepository.Scope.ALL, null);
        model.refresh(); session.clear(session.current()); queue.remove().run();
        assertTrue(repository.reads.isEmpty()); assertFalse(model.snapshotLoading());
        assertEquals(com.example.fintrack.app.core.error.AppError.SESSION_EXPIRED, model.state().getValue().getError().code);
    }
    @Test public void clearedModelStaysSilentAfterSessionEnds() {
        FinanceViewModel model = model(); ViewModelStore owner = new ViewModelStore();
        new ViewModelProvider(owner, new ViewModelProvider.Factory() {
            @Override public <T extends ViewModel> T create(Class<T> type) { return type.cast(model); }
        }).get(FinanceViewModel.class);
        model.refresh(); owner.clear(); session.clear(session.current());
        repository.reads.get(0).deliver(Resource.success(book()));
        assertEquals(Resource.Status.LOADING, model.state().getValue().getStatus());
    }
    @Test public void clearingOwnerCancelsReadsAndSuppressesLaterCallbacks() {
        FinanceViewModel model = model();
        ViewModelStore owner = new ViewModelStore();
        new ViewModelProvider(owner, new ViewModelProvider.Factory() {
            @Override public <T extends ViewModel> T create(Class<T> type) { return type.cast(model); }
        }).get(FinanceViewModel.class);
        model.refresh(); owner.clear(); assertTrue(repository.reads.get(0).cancelled);
        repository.reads.get(0).deliver(Resource.success(book()));
        assertEquals(Resource.Status.LOADING, model.state().getValue().getStatus());
    }
    @Test public void clearingOwnerDoesNotCancelOrReplayAnUncertainWrite() {
        FinanceViewModel model = model(); ViewModelStore owner = new ViewModelStore();
        new ViewModelProvider(owner, new ViewModelProvider.Factory() {
            @Override public <T extends ViewModel> T create(Class<T> type) { return type.cast(model); }
        }).get(FinanceViewModel.class);
        model.change(FinanceAction.deleteTransaction("id"), true);
        model.change(FinanceAction.deleteTransaction("id"), true);
        assertEquals(1, repository.writes.size());
        owner.clear(); assertFalse(repository.writes.get(0).cancelled);
        repository.writes.get(0).deliver(Resource.success(new MutationReceipt(null, null)));
        assertNull(model.command().getValue()); assertEquals(1, repository.writes.size());
    }
    @Test public void queuedWriteDoesNotRunAfterSameAccountRelogin() {
        java.util.ArrayDeque<Runnable> queue = new java.util.ArrayDeque<>();
        AppExecutors queued = new AppExecutors(queue::add, queue::add, Runnable::run, Runnable::run);
        int[] mutations = {0};
        FinanceRepository realAsync = new FinanceRepository((MockFinanceStore) null) {
            @Override public MutationReceipt mutate(FinanceAction action, FinanceBook previous) {
                mutations[0]++; return new MutationReceipt(null, null);
            }
        };
        session.login("a", "r", true, true);
        FinanceViewModel model = new FinanceViewModel(session, origin -> realAsync, queued, FinanceRepository.Scope.ALL, null);
        model.change(FinanceAction.deleteTransaction("id"), true);
        queue.remove().run(); // Resolve the repository, leaving its write queued.
        session.clear(session.current()); session.login("b", "r", true, true);
        queue.remove().run();
        assertEquals(0, mutations[0]); assertNull(model.command().getValue());
    }
    @Test public void clearingOwnerKeepsQueuedWriteWhenItsSessionIsCurrent() {
        java.util.ArrayDeque<Runnable> queue = new java.util.ArrayDeque<>();
        AppExecutors queued = new AppExecutors(queue::add, queue::add, Runnable::run, Runnable::run);
        int[] mutations = {0};
        FinanceRepository realAsync = new FinanceRepository((MockFinanceStore) null) {
            @Override public MutationReceipt mutate(FinanceAction action, FinanceBook previous) {
                mutations[0]++; return new MutationReceipt(null, null);
            }
        };
        session.login("a", "r", true, true);
        FinanceViewModel model = new FinanceViewModel(session, origin -> realAsync, queued, FinanceRepository.Scope.ALL, null);
        ViewModelStore owner = new ViewModelStore();
        new ViewModelProvider(owner, new ViewModelProvider.Factory() {
            @Override public <T extends ViewModel> T create(Class<T> type) { return type.cast(model); }
        }).get(FinanceViewModel.class);
        model.change(FinanceAction.deleteTransaction("id"), true);
        queue.remove().run(); owner.clear(); queue.remove().run();
        assertEquals(1, mutations[0]); assertNull(model.command().getValue());
    }
}
