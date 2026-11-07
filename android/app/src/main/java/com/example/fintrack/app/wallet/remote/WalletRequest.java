package com.example.fintrack.app.wallet.remote;

import com.example.fintrack.app.finance.domain.FinanceBook;
import com.google.gson.annotations.SerializedName;

/** Request DTO đóng gói dữ liệu khi Tạo mới Ví tiền gửi lên Server */
public class WalletRequest {

    @SerializedName("name")
    public final String name;

    @SerializedName("walletType")
    public final FinanceBook.WalletType walletType;

    @SerializedName("openingBalanceMinor")
    public final long openingBalanceMinor;

    public final boolean includeInAssets;
    public final int sortOrder;

    public WalletRequest(String name, long amount, FinanceBook.WalletType type) {
        this(name, amount, type, true, 0);
    }

    public WalletRequest(String name, long amount, FinanceBook.WalletType type, boolean includeInAssets, int sortOrder) {
        this.name = name;
        openingBalanceMinor = amount;
        walletType = type;
        this.includeInAssets = includeInAssets;
        this.sortOrder = sortOrder;
    }
}
