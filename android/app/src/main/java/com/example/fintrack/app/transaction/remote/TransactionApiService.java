package com.example.fintrack.app.transaction.remote;

import retrofit2.Call;
import retrofit2.http.*;

public interface TransactionApiService {

    @GET("api/v1/transactions")
    Call<TransactionDtos.TransactionPage> transactions(@Query("page") int page, @Query("size") int size);

    @POST("api/v1/transactions")
    Call<TransactionDtos.Transaction> addTransaction(@Body TransactionDtos.TransactionRequest request);

    @PUT("api/v1/transactions/{id}")
    Call<TransactionDtos.Transaction> updateTransaction(@Path("id") String id, @Body TransactionDtos.TransactionRequest request);

    @DELETE("api/v1/transactions/{id}")
    Call<Void> deleteTransaction(@Path("id") String id);
}
