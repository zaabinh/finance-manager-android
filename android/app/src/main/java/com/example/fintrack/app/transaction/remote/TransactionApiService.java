package com.example.fintrack.app.transaction.remote;

import retrofit2.Call;
import retrofit2.http.*;

/**
 * Interface định nghĩa các API endpoint liên quan đến Giao dịch tài chính (Transactions)
 * sử dụng thư viện Retrofit.
 */
public interface TransactionApiService {

    /** API Lấy danh sách giao dịch phân trang từ Server */
    @GET("api/v1/transactions")
    Call<TransactionPageResponse> transactions(@Query("page") int page, @Query("size") int size);

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
