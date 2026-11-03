package com.example.fintrack.app.transaction.remote;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/** Response DTO nhận danh sách giao dịch phân trang từ Server */
public class TransactionPageResponse {

    @SerializedName("items")
    public List<TransactionResponse> items;

    @SerializedName("page")
    public int page;

    @SerializedName("totalPages")
    public int totalPages;
}
