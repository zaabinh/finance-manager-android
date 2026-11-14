package com.example.fintrack.app.transaction.domain;

import com.example.fintrack.app.finance.domain.Category;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.FinanceFailure;
import com.example.fintrack.app.finance.domain.Transaction;
import com.example.fintrack.app.finance.domain.TransactionType;
import com.example.fintrack.app.finance.domain.Wallet;

/**
 * Client-side checks of the transaction form, in the order the user is told about them. The authoritative
 * rules stay in {@link FinanceBook}; this only avoids a round trip for obvious mistakes.
 */
public final class TransactionFormValidator {
    private TransactionFormValidator() { }

    public enum Error {
        MISSING_CATEGORY, MISSING_WALLET, MISSING_TRANSFER_WALLETS, SAME_WALLET,
        WALLET_ARCHIVED, CATEGORY_ARCHIVED, INVALID_AMOUNT
    }

    public static final class Result {
        /** The validated value (without clientRequestId), or null when {@link #error} is set. */
        public final Transaction transaction;
        public final Error error;
        private Result(Transaction transaction, Error error) { this.transaction = transaction; this.error = error; }
        public boolean valid() { return error == null; }
    }

    public static Result validate(FinanceBook book, String id, TransactionType type, String category, String wallet,
            String destination, long occurredAt, String amountText, String note, String payee) {
        boolean transfer = type == TransactionType.TRANSFER;
        if (!transfer && (category == null || category.isEmpty())) return fail(Error.MISSING_CATEGORY);
        if (wallet == null || wallet.isEmpty() || (transfer && (destination == null || destination.isEmpty())))
            return fail(transfer ? Error.MISSING_TRANSFER_WALLETS : Error.MISSING_WALLET);
        if (transfer && wallet.equals(destination)) return fail(Error.SAME_WALLET);
        // Contract: POST/PUT need active wallets (both for a transfer) and, for income/expense, an active category of the same type.
        Wallet selectedWallet = book.wallet(wallet), selectedDestination = transfer ? book.wallet(destination) : null;
        if (selectedWallet == null || selectedWallet.archivedAt != null
                || (transfer && (selectedDestination == null || selectedDestination.archivedAt != null))) return fail(Error.WALLET_ARCHIVED);
        if (!transfer) {
            Category selectedCategory = book.category(category);
            if (selectedCategory == null || selectedCategory.archivedAt != null || selectedCategory.type != type)
                return fail(Error.CATEGORY_ARCHIVED);
        }
        long amount;
        try { amount = FinanceBook.parseAmount(amountText); }
        catch (FinanceFailure error) { return fail(Error.INVALID_AMOUNT); }
        return new Result(draft(id, type, amount, category, wallet, destination, occurredAt, note, payee), null);
    }

    /** Form to domain value: a transfer never carries a category, income/expense never a destination. */
    static Transaction draft(String id, TransactionType type, long amount, String category, String wallet,
            String destination, long occurredAt, String note, String payee) {
        boolean transfer = type == TransactionType.TRANSFER;
        return new Transaction(id, type, amount, transfer ? null : category, wallet, transfer ? destination : null,
                occurredAt, note, payee);
    }

    private static Result fail(Error error) { return new Result(null, error); }
}
