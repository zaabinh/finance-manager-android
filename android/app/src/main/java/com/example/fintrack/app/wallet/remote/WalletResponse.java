package com.example.fintrack.app.wallet.remote;

import com.example.fintrack.app.finance.domain.Wallet;
import com.example.fintrack.app.finance.domain.WalletType;

import com.google.gson.annotations.SerializedName;

/** Response DTO nhận dữ liệu Ví tiền trả về từ Server */
public class WalletResponse {

    @SerializedName("walletId")
    public String walletId;

    @SerializedName("name")
    public String name;

    @SerializedName("walletType")
    public WalletType walletType;

    @SerializedName("openingBalanceMinor")
    public long openingBalanceMinor;

    public long balanceMinor;
    public String archivedAt;
    public boolean includeInAssets = true;
    public int sortOrder;
    public String currencyCode, createdAt, updatedAt;

    /** Chuyển đổi Response DTO sang mô hình dữ liệu Domain */
    public Wallet domain() {
        return new Wallet(walletId,name,openingBalanceMinor,walletType,archivedAt,includeInAssets,sortOrder,balanceMinor);
    }
}
