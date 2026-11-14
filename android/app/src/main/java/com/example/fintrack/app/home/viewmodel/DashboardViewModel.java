package com.example.fintrack.app.home.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.fintrack.app.core.error.AppError;
import com.example.fintrack.app.core.network.RequestHandle;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.SessionSnapshot;
import com.example.fintrack.app.core.session.SessionStore;
import com.example.fintrack.app.home.remote.DashboardResponse;
import com.example.fintrack.app.home.repository.DashboardRepository;

/**
 * Home summary from GET /api/v1/dashboard (real backend; the mock flavor derives it from the local ledger).
 * A response belongs to the session that asked for it: once that session has ended its data is never delivered;
 * instead loading stops and expiry is signalled once as an UNAUTHORIZED error. A newer refresh cancels the older one.
 */
public class DashboardViewModel extends ViewModel {
    private final DashboardRepository repository;
    private final SessionStore sessionStore;
    private final MutableLiveData<Resource<DashboardResponse>> summary = new MutableLiveData<>();
    private RequestHandle read = RequestHandle.NONE;
    private boolean expired; // expiry already signalled since the last refresh that started

    public DashboardViewModel(DashboardRepository repository, SessionStore sessionStore) {
        this.repository = repository;
        this.sessionStore = sessionStore;
    }

    public LiveData<Resource<DashboardResponse>> summary() { return summary; }

    public void refresh() {
        SessionSnapshot origin = sessionStore.current();
        read.cancel();
        if (!sessionStore.isCurrent(origin)) { expire(); return; }
        expired = false;
        summary.setValue(Resource.loading(null));
        read = repository.dashboard(result -> {
            if (sessionStore.isCurrent(origin)) summary.setValue(result);
            else expire();
        });
    }

    private void expire() {
        if (expired) return;
        expired = true;
        summary.setValue(Resource.error(AppError.sessionExpired(), null));
    }

    @Override protected void onCleared() { read.cancel(); }
}
