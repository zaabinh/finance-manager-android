package com.example.fintrack.app.transaction.remote;

import com.example.fintrack.app.finance.domain.Transaction;
import com.example.fintrack.app.finance.domain.TransactionType;

import com.google.gson.annotations.SerializedName;

/** Request DTO đóng gói dữ liệu khi Tạo hoặc Sửa Giao dịch gửi lên Server */
public class TransactionRequest {

    @SerializedName("clientRequestId")
    public final String clientRequestId;

    @SerializedName("categoryId")
    public final String categoryId;

    @SerializedName("walletId")
    public final String walletId;

    @SerializedName("destinationWalletId")
    public final String destinationWalletId;

    @SerializedName("occurredAt")
    public final String occurredAt;

    @SerializedName("note")
    public final String note;

    @SerializedName("payee")
    public final String payee;

    @SerializedName("transactionType")
    public final TransactionType transactionType;

    @SerializedName("amountMinor")
    public final long amountMinor;

    /** PUT omits clientRequestId (contract section 5); POST keeps the form's stable key across retries. */
    public TransactionRequest(Transaction value, boolean editing) {
        clientRequestId = editing ? null : value.clientRequestId != null ? value.clientRequestId : value.id;
        transactionType = value.type;
        amountMinor = value.amount;
        categoryId = value.type == TransactionType.TRANSFER ? null : value.categoryId;
        walletId = value.walletId;
        destinationWalletId = value.type == TransactionType.TRANSFER ? value.destinationWalletId : null;
        occurredAt = TransactionResponse.timestamp(value.occurredAt);
        note = value.note;
        payee = value.payee;
    }
}
