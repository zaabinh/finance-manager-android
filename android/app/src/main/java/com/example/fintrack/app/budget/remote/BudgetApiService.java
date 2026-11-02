package com.example.fintrack.app.budget.remote;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.*;

public interface BudgetApiService {

    @GET("api/v1/budgets")
    Call<List<BudgetDtos.Budget>> budgets();

    @POST("api/v1/budgets")
    Call<BudgetDtos.Budget> addBudget(@Body BudgetDtos.BudgetRequest request);

    @PUT("api/v1/budgets/{id}")
    Call<BudgetDtos.Budget> updateBudget(@Path("id") String id, @Body BudgetDtos.BudgetRequest request);

    @DELETE("api/v1/budgets/{id}")
    Call<Void> deleteBudget(@Path("id") String id);
}
