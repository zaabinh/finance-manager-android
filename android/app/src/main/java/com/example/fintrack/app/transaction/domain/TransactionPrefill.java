package com.example.fintrack.app.transaction.domain;

import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.TransactionType;
import com.example.fintrack.app.finance.domain.Wallet;

/**
 * Optional form prefill from another screen (e.g. budget shortfall → "Chuyển từ tiết kiệm").
 * It only fills the form; the user still confirms and saves. Invalid values and unknown/archived
 * wallet IDs are dropped silently so the form opens with whatever is still usable.
 */
public final class TransactionPrefill {
    public final TransactionType type;
    public final String walletId, destinationWalletId;
    public final Long amountMinor;

    private TransactionPrefill(TransactionType type, String walletId, String destinationWalletId, Long amountMinor) {
        this.type = type; this.walletId = walletId; this.destinationWalletId = destinationWalletId; this.amountMinor = amountMinor;
    }

    /** Parses raw Intent extras; returns null when nothing usable was supplied. amountMinor null means absent. */
    public static TransactionPrefill parse(String type, String walletId, String destinationWalletId, Long amountMinor) {
        TransactionType parsedType = null;
        if (type != null) {
            for (TransactionType value : TransactionType.values()) if (value.name().equals(type.trim())) parsedType = value;
        }
        Long amount = amountMinor != null && amountMinor > 0 && amountMinor <= FinanceBook.MAX_AMOUNT ? amountMinor : null;
        String wallet = blank(walletId) ? null : walletId.trim();
        String destination = blank(destinationWalletId) ? null : destinationWalletId.trim();
        if (parsedType == null && wallet == null && destination == null && amount == null) return null;
        return new TransactionPrefill(parsedType, wallet, destination, amount);
    }

    /** Keeps only wallets that exist and are active in the loaded references; a destination needs a TRANSFER. */
    public TransactionPrefill resolve(FinanceBook book) {
        TransactionType resolvedType = type == null ? TransactionType.EXPENSE : type;
        String wallet = active(book, walletId) ? walletId : null;
        String destination = resolvedType == TransactionType.TRANSFER && active(book, destinationWalletId)
                && !destinationWalletId.equals(wallet) ? destinationWalletId : null;
        return new TransactionPrefill(resolvedType, wallet, destination, amountMinor);
    }

    private static boolean active(FinanceBook book, String id) {
        if (id == null || book == null) return false;
        Wallet wallet = book.wallet(id);
        return wallet != null && wallet.archivedAt == null;
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
