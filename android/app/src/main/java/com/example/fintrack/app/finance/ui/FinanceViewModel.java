package com.example.fintrack.app.finance.ui;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.SessionManager;
import com.example.fintrack.app.finance.data.FinanceRepository;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.domain.FinanceBook;
import java.util.function.Consumer;

public final class FinanceViewModel extends AndroidViewModel {
    private final MutableLiveData<Resource<FinanceBook>> state = new MutableLiveData<>();
    private final MutableLiveData<Command> command = new MutableLiveData<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final String owner;
    private boolean busy;
    public static final class Command {
        public final FinanceBook.Problem error;
        public final boolean complete;
        private boolean consumed;
        Command(FinanceBook.Problem error, boolean complete) { this.error = error; this.complete = complete; }
        public boolean consume() { if (consumed) return false; consumed = true; return true; }
    }
    public FinanceViewModel(@NonNull Application application) {
        super(application);
        owner = SessionManager.getInstance(application).getUserId();
    }
    public LiveData<Resource<FinanceBook>> state() { return state; }
    public LiveData<Command> command() { return command; }
    public boolean busy() { return busy; }
    public void refresh() { if (!busy) execute(null, false); }
    public void change(FinanceAction mutation, boolean complete) { if (!busy) execute(mutation, complete); }
    private boolean sameSession() {
        SessionManager session = SessionManager.getInstance(getApplication());
        return session.isLoggedIn() && owner != null && owner.equals(session.getUserId());
    }
    private void execute(FinanceAction mutation, boolean complete) {
        if (!sameSession()) return;
        busy = true;
        Resource<FinanceBook> old = state.getValue();
        FinanceBook previous = old == null ? null : old.getData();
        state.setValue(Resource.loading(previous));
        FinanceRepository.IO.execute(() -> {
            try {
                if (!sameSession()) throw new FinanceBook.Failure(FinanceBook.Problem.UNAUTHORIZED);
                FinanceRepository repository = new FinanceRepository(getApplication(), owner);
                FinanceBook book = mutation == null ? repository.read() : repository.mutate(mutation, previous);
                main.post(() -> {
                    busy = false;
                    if (!sameSession()) return;
                    state.setValue(Resource.success(book == null ? previous : book));
                    if (mutation != null) command.setValue(new Command(null, complete));
                    if (mutation != null && !complete && book == null) refresh();
                });
            } catch (Exception e) {
                FinanceBook.Problem problem = e instanceof FinanceBook.Failure
                        ? ((FinanceBook.Failure) e).problem : com.example.fintrack.BuildConfig.USE_MOCK
                        ? FinanceBook.Problem.STORAGE : FinanceBook.Problem.SERVER;
                main.post(() -> {
                    busy = false;
                    if (!sameSession() && problem != FinanceBook.Problem.UNAUTHORIZED) return;
                    state.setValue(Resource.error(problem.name(), previous));
                    if (mutation != null) command.setValue(new Command(problem, false));
                });
            }
        });
    }
}
