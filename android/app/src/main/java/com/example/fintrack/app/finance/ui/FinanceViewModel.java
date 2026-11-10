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

/**
 * ViewModel quản lý trạng thái dữ liệu tài chính (FinanceBook) và các lệnh thao tác (Commands),
 * đảm bảo thực hiện các tác vụ nặng trên luồng nền (Background IO Thread) và cập nhật UI qua LiveData.
 */
public final class FinanceViewModel extends AndroidViewModel {
    
    // LiveData lưu trữ trạng thái dữ liệu tài chính hiện tại (Loading, Success, Error)
    private final MutableLiveData<Resource<FinanceBook>> state = new MutableLiveData<>();
    
    // LiveData lưu trữ sự kiện lệnh thực thi (thêm, sửa, xóa) để UI hiển thị thông báo
    private final MutableLiveData<Command> command = new MutableLiveData<>();
    
    private final Handler main = new Handler(Looper.getMainLooper());
    private final String owner;
    private boolean busy;
    private FinanceRepository.Scope scope = FinanceRepository.Scope.BUDGETS;
    private String detailId;
    // Transaction history (server paged). Survives rotation with the ViewModel.
    private FinanceBook.Query query;
    private java.util.List<FinanceBook.Transaction> rows = java.util.Collections.emptyList();
    private int page = -1, totalPages;

    /** Lớp đại diện cho một lệnh thao tác (Command) thành công hoặc thất bại */
    public static final class Command {
        public final FinanceBook.Problem error;
        public final boolean complete;
        public final String message;
        /** Server errorCode/fieldErrors when known; null otherwise. */
        public final FinanceBook.Failure failure;
        private boolean consumed;

        Command(FinanceBook.Problem error, boolean complete) { this(error, complete, null, null); }
        Command(FinanceBook.Problem error, boolean complete, String message, FinanceBook.Failure failure) {
            this.error = error;
            this.complete = complete;
            this.message = message;
            this.failure = failure;
        }

        public boolean consume() {
            if (consumed) return false;
            consumed = true;
            return true;
        }
    }

    public FinanceViewModel(@NonNull Application application) {
        super(application);
        owner = SessionManager.getInstance(application).getUserId();
    }

    public LiveData<Resource<FinanceBook>> state() { return state; }
    public LiveData<Command> command() { return command; }
    public boolean busy() { return busy; }
    public void configure(FinanceRepository.Scope scope, String detailId) { this.scope = scope; this.detailId = detailId; }

    /** Làm mới dữ liệu tài chính từ server hoặc bộ nhớ local (trang lịch sử quay về page 0) */
    public void refresh() { if (!busy) execute(null, false, 0); }

    /** Thực hiện một hành động thay đổi dữ liệu (thêm/sửa/xóa giao dịch, ví, ngân sách) */
    public void change(FinanceAction mutation, boolean complete) { if (!busy) execute(mutation, complete, 0); }

    public FinanceBook.Query query() { return query; }
    public java.util.List<FinanceBook.Transaction> rows() { return rows; }
    public boolean hasMore() { return page + 1 < totalPages; }
    /** Changes the history filter and reloads page 0; an in-flight response for the old filter is dropped. */
    public void query(FinanceBook.Query value) {
        if (value.equals(query)) return;
        query = value; rows = java.util.Collections.emptyList(); page = -1; totalPages = 0;
        refresh();
    }
    public void more() { if (!busy && query != null && page >= 0 && hasMore()) execute(null, false, page + 1); }

    /** Kiểm tra xem phiên đăng nhập của user có khớp không */
    private boolean sameSession() {
        SessionManager session = SessionManager.getInstance(getApplication());
        return session.isLoggedIn() && owner != null && owner.equals(session.getUserId());
    }

    /** Thực thi tác vụ đọc hoặc ghi dữ liệu trên luồng nền (Background IO Thread) */
    private void execute(FinanceAction mutation, boolean complete, int pageIndex) {
        if (!sameSession()) return;
        busy = true;
        final FinanceBook.Query requested = query;
        Resource<FinanceBook> old = state.getValue();
        FinanceBook previous = old == null ? null : old.getData();
        state.setValue(Resource.loading(previous));

        FinanceRepository.IO.execute(() -> {
            try {
                if (!sameSession()) throw new FinanceBook.Failure(FinanceBook.Problem.UNAUTHORIZED);
                FinanceRepository repository = new FinanceRepository(getApplication(), owner);
                FinanceBook book = mutation != null ? repository.mutate(mutation, previous)
                        : pageIndex > 0 ? null : repository.read(scope, detailId);
                FinanceBook.Page result = mutation == null && requested != null ? repository.transactions(requested, pageIndex) : null;

                main.post(() -> {
                    busy = false;
                    if (!sameSession()) return;
                    if (requested != query && mutation == null) { refresh(); return; }
                    if (result != null) {
                        rows = pageIndex == 0 ? result.items : FinanceBook.merge(rows, result.items);
                        page = pageIndex; totalPages = result.totalPages;
                    }
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
                    if (requested != query && mutation == null) { refresh(); return; }
                    state.setValue(Resource.error(problem.name(), previous));
                    FinanceBook.Failure failure = e instanceof FinanceBook.Failure ? (FinanceBook.Failure) e : null;
                    if (mutation != null) command.setValue(new Command(problem, false, failure == null ? null : failure.userMessage, failure));
                });
            }
        });
    }
}
