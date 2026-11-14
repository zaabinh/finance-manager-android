package com.example.fintrack.app.home.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.fintrack.app.core.network.RequestHandle;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.SessionSnapshot;
import com.example.fintrack.app.core.session.SessionStore;
import com.example.fintrack.app.home.remote.DashboardResponse;
import com.example.fintrack.app.home.repository.DashboardRepository;

/**
 * Home summary from GET /api/v1/dashboard (real backend; the mock flavor derives it from the local ledger).
 * A response belongs to the session that asked for it: once that session has ended, or a newer refresh has
 * started, the response is dropped.
 */
public class DashboardViewModel extends ViewModel {
    private final DashboardRepository repository;
    private final SessionStore sessionStore;
    private final MutableLiveData<Resource<DashboardResponse>> summary = new MutableLiveData<>();
    private RequestHandle read = RequestHandle.NONE;

    public DashboardViewModel(DashboardRepository repository, SessionStore sessionStore) {
        this.repository = repository;
        this.sessionStore = sessionStore;
    }

    public LiveData<Resource<DashboardResponse>> summary() { return summary; }

    public void refresh() {
        SessionSnapshot origin = sessionStore.current();
        summary.setValue(Resource.loading(null));
        read.cancel();
        read = repository.dashboard(result -> {
            if (sessionStore.isCurrent(origin)) summary.setValue(result);
        });
    }

    @Override protected void onCleared() { read.cancel(); }
}
