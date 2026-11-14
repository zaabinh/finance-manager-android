package com.example.fintrack.app.home.repository;

import com.example.fintrack.app.core.error.AppError;
import com.example.fintrack.app.core.network.ApiCalls;
import com.example.fintrack.app.core.network.RequestHandle;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.home.remote.DashboardApiService;
import com.example.fintrack.app.home.remote.DashboardResponse;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Transport only. The dashboard is plain JSON without the ApiResponse envelope; its aggregates come from the server. */
public class DashboardRepository {
    private final DashboardApiService apiService;
    private final Executor delivery;
    public DashboardRepository(DashboardApiService apiService, Executor delivery) {
        this.apiService = apiService; this.delivery = delivery;
    }
    public RequestHandle dashboard(Consumer<Resource<DashboardResponse>> result) {
        return ApiCalls.enqueueBody(apiService.dashboard(), true, delivery, response -> {
            DashboardResponse body = response.getData();
            // A summary without its assets or budget block cannot be shown: same error as a missing body.
            result.accept(response.getStatus() == Resource.Status.SUCCESS && (body.assets == null || body.budget == null)
                    ? Resource.error(new AppError(AppError.Category.HTTP, null, "LOCAL_MISSING_DATA", null, null, null), null)
                    : response);
        });
    }
}
