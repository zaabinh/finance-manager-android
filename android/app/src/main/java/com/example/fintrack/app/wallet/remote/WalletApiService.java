package com.example.fintrack.app.wallet.remote;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.*;

/**
 * Interface định nghĩa các API endpoint liên quan đến Ví tài chính (Wallets)
 * sử dụng thư viện Retrofit.
 */
public interface WalletApiService {

    /** API Lấy danh sách ví tài chính của người dùng */
    @GET("api/v1/wallets")
    Call<List<WalletResponse>> wallets();

    /** API Tạo mới một ví tài chính */
    @POST("api/v1/wallets")
    Call<WalletResponse> addWallet(@Body WalletRequest request);
}
