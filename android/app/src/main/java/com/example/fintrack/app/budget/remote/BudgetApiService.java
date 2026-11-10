package com.example.fintrack.app.budget.remote;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.*;

/**
 * Interface định nghĩa các API endpoint liên quan đến Ngân sách (Budgets)
 * sử dụng thư viện Retrofit.
 */
public interface BudgetApiService {

    /** API Lấy danh sách ngân sách của người dùng */
    @GET("api/v1/budgets")
    Call<List<BudgetResponse>> budgets();

    @GET("api/v1/budgets")
    Call<List<BudgetResponse>> budgets(@Query("month") String month, @Query("categoryId") String categoryId,
            @Query("status") String status);

    @GET("api/v1/budgets/{id}")
    Call<BudgetResponse> budget(@Path("id") String id);

    /** API Thiết lập một khoản ngân sách mới */
    @POST("api/v1/budgets")
    Call<BudgetResponse> addBudget(@Body BudgetRequest request);

    /** API Cập nhật hạn mức ngân sách theo ID */
    @PUT("api/v1/budgets/{id}")
    Call<BudgetResponse> updateBudget(@Path("id") String id, @Body BudgetRequest request);

    /** Ngân sách tháng: danh sách (status ACTIVE|ARCHIVED|ALL), kèm các ngân sách danh mục */
    @GET("api/v1/monthly-budgets")
    Call<List<MonthlyBudgetResponse>> monthlyBudgets(@Query("month") String month, @Query("status") String status);

    @GET("api/v1/monthly-budgets/{id}")
    Call<MonthlyBudgetResponse> monthlyBudget(@Path("id") String id);

    /** Tạo ngân sách tháng cùng ít nhất một ngân sách danh mục (một request, nguyên tử) */
    @POST("api/v1/monthly-budgets")
    Call<MonthlyBudgetResponse> addMonthlyBudget(@Body MonthlyBudgetRequest request);

    /** Xóa ngân sách tháng (không cho tháng hiện tại); các ngân sách danh mục bị xóa theo */
    @DELETE("api/v1/monthly-budgets/{id}")
    Call<Void> deleteMonthlyBudget(@Path("id") String id);

    /** API Xóa một khoản ngân sách theo ID */
    @DELETE("api/v1/budgets/{id}")
    Call<Void> deleteBudget(@Path("id") String id);
}
