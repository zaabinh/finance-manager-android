package com.example.fintrack.app.finance.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.fintrack.app.core.di.AppExecutors;
import com.example.fintrack.app.core.error.AppError;
import com.example.fintrack.app.core.network.RequestHandle;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.SessionSnapshot;
import com.example.fintrack.app.core.session.SessionStore;
import com.example.fintrack.app.core.util.Event;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.data.FinanceRepository;
import com.example.fintrack.app.finance.data.MutationReceipt;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.FinanceFailure;
import com.example.fintrack.app.finance.domain.FinanceProblem;
import java.util.function.Consumer;
import java.util.function.Function;

/** One stable scoped snapshot and commands, bound to the login that created this model. */
public final class FinanceViewModel extends ViewModel {
    private final MutableLiveData<Resource<FinanceBook>> state = new MutableLiveData<>();
    private final MutableLiveData<Event<Command>> command = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saving = new MutableLiveData<>(false);
    private final SessionStore session;
    private final Function<SessionSnapshot, FinanceRepository> repositories;
    private final AppExecutors executors;
    private final SessionSnapshot origin;
    private final FinanceRepository.Scope scope;
    private final String detailId;
    private RequestHandle read = RequestHandle.NONE;
    private long generation;
    private boolean snapshotLoading, saveLoading, expired;
    private volatile boolean cleared;

    public static final class Command {
        public final FinanceProblem error;
        public final boolean complete;
        public final FinanceFailure failure;
        public final MutationReceipt receipt;
        Command(FinanceProblem error, boolean complete, FinanceFailure failure, MutationReceipt receipt) {
            this.error = error; this.complete = complete;
            this.failure = failure; this.receipt = receipt;
        }
    }

    public FinanceViewModel(SessionStore session, Function<SessionSnapshot, FinanceRepository> repositories,
                            AppExecutors executors, FinanceRepository.Scope scope, String detailId) {
        this.session = session; this.repositories = repositories; this.executors = executors;
        this.scope = java.util.Objects.requireNonNull(scope); this.detailId = detailId;
        origin = session.current();
    }
    public LiveData<Resource<FinanceBook>> state() { return state; }
    public LiveData<Event<Command>> command() { return command; }
    public LiveData<Boolean> saveLoadingState() { return saving; }
    public boolean snapshotLoading() { return snapshotLoading; }
    public boolean saveLoading() { return saveLoading; }
    public boolean busy() { return saveLoading; }
    private boolean active() { return !cleared && session.isCurrent(origin); }
    /**
     * For results and could-not-start outcomes, on the main thread. A cleared model stays silent; once the origin
     * session has ended, loading stops and expiry is signalled once as an UNAUTHORIZED error without data.
     */
    private boolean live() {
        if (cleared) return false;
        if (session.isCurrent(origin)) return true;
        snapshotLoading = false;
        if (saveLoading) { saveLoading = false; saving.setValue(false); }
        if (!expired) { expired = true; state.setValue(Resource.error(AppError.sessionExpired(), null)); }
        return false;
    }
    private FinanceBook previous() { return state.getValue() == null ? null : state.getValue().getData(); }

    public void refresh() {
        if (!active() || saveLoading) return;
        read.cancel();
        long requested = ++generation;
        FinanceBook previous = previous();
        snapshotLoading = true;
        state.setValue(Resource.loading(previous));
        resolve(repository -> {
            if (!live() || requested != generation) return;
            read = repository.readAsync(scope, detailId, executors, result -> {
                if (!live() || requested != generation) return;
                snapshotLoading = false;
                state.setValue(result.getStatus() == Resource.Status.ERROR
                        ? Resource.error(result.getError(), previous) : Resource.success(result.getData()));
            });
        }, error -> {
            if (!live() || requested != generation) return;
            snapshotLoading = false;
            state.setValue(Resource.error(error, previous));
        });
    }

    public void change(FinanceAction action, boolean complete) {
        if (!active() || saveLoading) return;
        ++generation;
        read.cancel();
        snapshotLoading = false;
        FinanceBook previous = previous();
        if (state.getValue() != null && state.getValue().getStatus() == Resource.Status.LOADING)
            state.setValue(Resource.success(previous));
        saveLoading = true;
        saving.setValue(true);
        resolve(repository -> repository.mutateAsync(action, previous, () -> session.isCurrent(origin), executors, result -> {
            if (!live()) return;
            saveLoading = false;
            saving.setValue(false);
            if (result.getStatus() == Resource.Status.ERROR) { failed(result.getError()); return; }
            MutationReceipt receipt = result.getData();
            if (receipt.snapshot != null) state.setValue(Resource.success(receipt.snapshot));
            command.setValue(new Event<>(new Command(null, complete, null, receipt)));
            if (!complete && receipt.snapshot == null) refresh();
        }), error -> {
            if (!live()) return;
            saveLoading = false;
            saving.setValue(false);
            failed(error);
        });
    }
    private void failed(AppError error) {
        command.setValue(new Event<>(new Command(FinanceErrors.problem(error), false, FinanceErrors.failure(error), null)));
    }
    private void resolve(Consumer<FinanceRepository> ready, Consumer<AppError> failed) {
        executors.serial().execute(() -> {
            if (!active()) { executors.main().execute(this::live); return; }
            try {
                FinanceRepository repository = repositories.apply(origin);
                executors.main().execute(() -> { if (live()) ready.accept(repository); });
            } catch (Exception error) {
                AppError failure = FinanceRepository.error(error);
                executors.main().execute(() -> { if (live()) failed.accept(failure); });
            }
        });
    }
    @Override protected void onCleared() {
        cleared = true;
        ++generation;
        read.cancel();
    }
}
