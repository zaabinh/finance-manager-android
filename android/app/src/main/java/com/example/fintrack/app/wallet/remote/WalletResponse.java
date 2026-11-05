package com.example.fintrack.app.wallet.remote;

import com.example.fintrack.app.finance.domain.FinanceBook;
import com.google.gson.annotations.SerializedName;

/** Response DTO nhận dữ liệu Ví tiền trả về từ Server */
public class WalletResponse {

    @SerializedName("walletId")
    public String walletId;

    @SerializedName("name")
    public String name;

    @SerializedName("walletType")
    public FinanceBook.WalletType walletType;

    @SerializedName("openingBalanceMinor")
    public long openingBalanceMinor;

    /** Chuyển đổi Response DTO sang mô hình dữ liệu Domain */
    public FinanceBook.Wallet domain() {
        FinanceBook.Wallet wallet = new FinanceBook.Wallet(walletId, name, openingBalanceMinor);
        wallet.walletType = walletType;
        return wallet;
    }
}
