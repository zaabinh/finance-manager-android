package com.example.fintrack.app.home.remote;

import retrofit2.Call;
import retrofit2.http.GET;

/** Home summary (docs/api/HOME_DASHBOARD.md): plain JSON, no ApiResponse envelope; month defaults to the user's current month. */
public interface DashboardApiService {
    @GET("api/v1/dashboard")
    Call<DashboardResponse> dashboard();
}
