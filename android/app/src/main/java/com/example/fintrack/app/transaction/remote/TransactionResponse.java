package com.example.fintrack.app.transaction.remote;

import com.example.fintrack.app.finance.domain.FinanceBook;
import com.google.gson.annotations.SerializedName;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Response DTO nhận dữ liệu Giao dịch trả về từ Server */
public class TransactionResponse {

    @SerializedName("transactionId")
    public String transactionId;

    @SerializedName("categoryId")
    public String categoryId;

    @SerializedName("walletId")
    public String walletId;

    @SerializedName("destinationWalletId")
    public String destinationWalletId;

    @SerializedName("occurredAt")
    public String occurredAt;

    @SerializedName("note")
    public String note;

    @SerializedName("payee")
    public String payee;

    @SerializedName("transactionType")
    public FinanceBook.Type transactionType;

    @SerializedName("amountMinor")
    public long amountMinor;

    @SerializedName("status") public String status;
    @SerializedName("voidedAt") public String voidedAt;
    @SerializedName("clientRequestId") public String clientRequestId;
    @SerializedName("createdAt") public String createdAt;
    @SerializedName("updatedAt") public String updatedAt;

    /** Chuyển đổi Response DTO sang mô hình dữ liệu Domain */
    public FinanceBook.Transaction domain() {
        FinanceBook.Transaction value = new FinanceBook.Transaction(transactionId, transactionType, amountMinor, categoryId, walletId,
                destinationWalletId, parseTimestamp(occurredAt), note, payee);
        value.status = status == null ? "POSTED" : status;
        value.voidedAt = voidedAt;
        value.clientRequestId = clientRequestId;
        return value;
    }

    public static String timestamp(long value) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(value));
    }

    public static long parseTimestamp(String text) {
        if (text == null || !text.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?Z"))
            throw new FinanceBook.Failure(FinanceBook.Problem.SERVER);

        String normalized = text.substring(0, 19) + ".";
        String fraction = text.length() > 20 ? text.substring(20, text.length() - 1) : "";
        normalized += (fraction + "000").substring(0, 3) + "Z";

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        format.setLenient(false);

        ParsePosition position = new ParsePosition(0);
        Date date = format.parse(normalized, position);
        if (date == null || position.getIndex() != normalized.length())
            throw new FinanceBook.Failure(FinanceBook.Problem.SERVER);

        return date.getTime();
    }
}
