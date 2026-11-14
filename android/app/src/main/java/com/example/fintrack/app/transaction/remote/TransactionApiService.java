package com.example.fintrack.app.transaction.remote;

import com.example.fintrack.app.finance.domain.TransactionType;

import retrofit2.Call;
import retrofit2.http.*;

/**
 * Interface định nghĩa các API endpoint liên quan đến Giao dịch tài chính (Transactions)
 * sử dụng thư viện Retrofit.
 */
public interface TransactionApiService {

    /** API Lấy danh sách giao dịch phân trang từ Server */
    /** Lịch sử phân trang theo contract mục 5; tham số null bị bỏ khỏi URL */
    @GET("api/v1/transactions")
    Call<TransactionPageResponse> transactions(@Query("page") int page, @Query("size") int size,
                                               @Query("dateFrom") String dateFrom, @Query("dateTo") String dateTo,
                                               @Query("walletId") String walletId, @Query("categoryId") String categoryId,
                                               @Query("type") TransactionType type, @Query("q") String q, @Query("sort") String sort);

    /** API Chi tiết giao dịch theo ID (kể cả VOIDED để audit) */
    @GET("api/v1/transactions/{id}")
    Call<TransactionResponse> transaction(@Path("id") String id);

    /** API Thêm mới một giao dịch (Thu nhập, Chi tiêu, Chuyển khoản) */
    @POST("api/v1/transactions")
    Call<TransactionResponse> addTransaction(@Body TransactionRequest request);

    /** API Cập nhật thông tin giao dịch theo ID */
    @PUT("api/v1/transactions/{id}")
    Call<TransactionResponse> updateTransaction(@Path("id") String id, @Body TransactionRequest request);

    /** API Xóa một giao dịch theo ID */
    @DELETE("api/v1/transactions/{id}")
    Call<Void> deleteTransaction(@Path("id") String id);
}
