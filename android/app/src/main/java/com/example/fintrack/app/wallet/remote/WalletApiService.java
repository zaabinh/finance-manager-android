package com.example.fintrack.app.wallet.remote;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.*;

public interface WalletApiService {

    @GET("api/v1/wallets")
    Call<List<WalletDtos.Wallet>> wallets();

    @POST("api/v1/wallets")
    Call<WalletDtos.Wallet> addWallet(@Body WalletDtos.WalletRequest request);
}
