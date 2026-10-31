package com.example.fintrack.app.finance.remote;

import com.example.fintrack.app.finance.domain.FinanceBook;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Wire models intentionally separate from the mock ledger's persistence models. */
public final class FinanceDtos {
    public static final class Wallet {
        public String walletId, name;
        public FinanceBook.WalletType walletType;
        public long openingBalanceMinor;
        public FinanceBook.Wallet domain() {
            FinanceBook.Wallet wallet = new FinanceBook.Wallet(walletId, name, openingBalanceMinor);
            wallet.walletType = walletType; return wallet;
        }
    }
    public static final class Category {
        public String categoryId, name;
        public FinanceBook.Type categoryType;
        public FinanceBook.Category domain() { return new FinanceBook.Category(categoryId, name, categoryType); }
    }
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
    public static final class Budget {
        public String budgetId, categoryId, month;
        public long limitMinor;
        public FinanceBook.Budget domain() { return new FinanceBook.Budget(budgetId, categoryId, month, limitMinor); }
    }
    public static final class TransactionRequest {
        public final String clientRequestId, categoryId, walletId, destinationWalletId, occurredAt, note, payee;
        public final FinanceBook.Type transactionType;
        public final long amountMinor;
        public TransactionRequest(FinanceBook.Transaction value) {
            clientRequestId = value.id; transactionType = value.type; amountMinor = value.amount;
            categoryId = value.type == FinanceBook.Type.TRANSFER ? null : value.categoryId;
            walletId = value.walletId; destinationWalletId = value.type == FinanceBook.Type.TRANSFER ? value.destinationWalletId : null;
            occurredAt = timestamp(value.occurredAt); note = value.note; payee = value.payee;
        }
    }
    public static final class BudgetRequest {
        public final String categoryId, month;
        public final long limitMinor;
        public BudgetRequest(FinanceBook.Budget value) { categoryId = value.categoryId; month = value.month; limitMinor = value.limit; }
    }
    public static final class WalletRequest {
        public final String name;
        public final FinanceBook.WalletType walletType;
        public final long openingBalanceMinor;
        public WalletRequest(String name, long amount, FinanceBook.WalletType type) { this.name = name; openingBalanceMinor = amount; walletType = type; }
    }
    public static final class CategoryRequest {
        public final String name;
        public final FinanceBook.Type categoryType;
        public CategoryRequest(String name, FinanceBook.Type type) { this.name = name; categoryType = type; }
    }
    public static String timestamp(long value) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC")); return format.format(new Date(value));
    }
    public static long parseTimestamp(String text) {
        if (text == null || !text.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?Z"))
            throw new FinanceBook.Failure(FinanceBook.Problem.SERVER);
        // Contract uses UTC; accept server precision beyond milliseconds by truncating it.
        String normalized = text.substring(0, 19) + ".";
        String fraction = text.length() > 20 ? text.substring(20, text.length() - 1) : "";
        normalized += (fraction + "000").substring(0, 3) + "Z";
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC")); format.setLenient(false);
        ParsePosition position = new ParsePosition(0); Date date = format.parse(normalized, position);
        if (date == null || position.getIndex() != normalized.length()) throw new FinanceBook.Failure(FinanceBook.Problem.SERVER);
        return date.getTime();
    }
    private FinanceDtos() { }
}
