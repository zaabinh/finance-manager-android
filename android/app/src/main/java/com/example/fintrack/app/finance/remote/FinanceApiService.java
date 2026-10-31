package com.example.fintrack.app.finance.remote;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.*;

/** Proposed server contract: docs/api/TRANSACTION_BUDGET_ANDROID.md. DTOs are direct bodies. */
public interface FinanceApiService {
    @GET("api/v1/wallets") Call<List<FinanceDtos.Wallet>> wallets();
    @POST("api/v1/wallets") Call<FinanceDtos.Wallet> addWallet(@Body FinanceDtos.WalletRequest request);
    @GET("api/v1/categories") Call<List<FinanceDtos.Category>> categories();
    @POST("api/v1/categories") Call<FinanceDtos.Category> addCategory(@Body FinanceDtos.CategoryRequest request);
    @GET("api/v1/transactions") Call<FinanceDtos.TransactionPage> transactions(@Query("page") int page, @Query("size") int size);
    @POST("api/v1/transactions") Call<FinanceDtos.Transaction> addTransaction(@Body FinanceDtos.TransactionRequest request);
    @PUT("api/v1/transactions/{id}") Call<FinanceDtos.Transaction> updateTransaction(@Path("id") String id, @Body FinanceDtos.TransactionRequest request);
    @DELETE("api/v1/transactions/{id}") Call<Void> deleteTransaction(@Path("id") String id);
    @GET("api/v1/budgets") Call<List<FinanceDtos.Budget>> budgets();
    @POST("api/v1/budgets") Call<FinanceDtos.Budget> addBudget(@Body FinanceDtos.BudgetRequest request);
    @PUT("api/v1/budgets/{id}") Call<FinanceDtos.Budget> updateBudget(@Path("id") String id, @Body FinanceDtos.BudgetRequest request);
    @DELETE("api/v1/budgets/{id}") Call<Void> deleteBudget(@Path("id") String id);
}
