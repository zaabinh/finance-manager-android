package com.example.fintrack.app.transaction.remote;

import com.example.fintrack.app.finance.domain.FinanceBook;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class TransactionDtos {

    public static final class Transaction {
        public String transactionId, categoryId, walletId, destinationWalletId, occurredAt, note, payee;
        public FinanceBook.Type transactionType;
        public long amountMinor;

        public FinanceBook.Transaction domain() {
            return new FinanceBook.Transaction(transactionId, transactionType, amountMinor, categoryId, walletId,
                    destinationWalletId, parseTimestamp(occurredAt), note, payee);
        }
    }

    public static final class TransactionPage {
        public List<Transaction> items;
        public int page, totalPages;
    }

    public static final class TransactionRequest {
        public final String clientRequestId, categoryId, walletId, destinationWalletId, occurredAt, note, payee;
        public final FinanceBook.Type transactionType;
        public final long amountMinor;

        public TransactionRequest(FinanceBook.Transaction value) {
            clientRequestId = value.id;
            transactionType = value.type;
            amountMinor = value.amount;
            categoryId = value.type == FinanceBook.Type.TRANSFER ? null : value.categoryId;
            walletId = value.walletId;
            destinationWalletId = value.type == FinanceBook.Type.TRANSFER ? value.destinationWalletId : null;
            occurredAt = timestamp(value.occurredAt);
            note = value.note;
            payee = value.payee;
        }
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

    private TransactionDtos() { }
}
