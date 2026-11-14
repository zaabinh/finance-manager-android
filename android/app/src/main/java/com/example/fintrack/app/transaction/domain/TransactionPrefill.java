package com.example.fintrack.app.transaction.domain;

import com.example.fintrack.app.finance.domain.Category;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.TransactionType;
import com.example.fintrack.app.finance.domain.Wallet;

/**
 * Optional form prefill from another screen (e.g. budget shortfall → "Chuyển từ tiết kiệm",
 * voice input → category, wallet, amount, type).
 * It only fills the form; the user still confirms and saves. Invalid values and unknown/archived
 * wallet IDs are dropped silently so the form opens with whatever is still usable.
 */
public final class TransactionPrefill {
    public final TransactionType type;
    public final String walletId, destinationWalletId;
    public final Long amountMinor;
    public final String categoryId;

    private TransactionPrefill(TransactionType type, String walletId, String destinationWalletId, Long amountMinor, String categoryId) {
        this.type = type;
        this.walletId = walletId;
        this.destinationWalletId = destinationWalletId;
        this.amountMinor = amountMinor;
        this.categoryId = categoryId;
    }

    /** Parses raw Intent extras; returns null when nothing usable was supplied. amountMinor null means absent. */
    public static TransactionPrefill parse(String type, String walletId, String destinationWalletId, Long amountMinor) {
        return parse(type, walletId, destinationWalletId, amountMinor, null);
    }

    public static TransactionPrefill parse(String type, String walletId, String destinationWalletId, Long amountMinor, String categoryId) {
        TransactionType parsedType = null;
        if (type != null) {
            for (TransactionType value : TransactionType.values()) if (value.name().equals(type.trim())) parsedType = value;
        }
        Long amount = amountMinor != null && amountMinor > 0 && amountMinor <= FinanceBook.MAX_AMOUNT ? amountMinor : null;
        String wallet = blank(walletId) ? null : walletId.trim();
        String destination = blank(destinationWalletId) ? null : destinationWalletId.trim();
        String category = blank(categoryId) ? null : categoryId.trim();
        if (parsedType == null && wallet == null && destination == null && amount == null && category == null) return null;
        return new TransactionPrefill(parsedType, wallet, destination, amount, category);
    }

    /** Keeps only wallets and categories that exist and are active in the loaded references; a destination needs a TRANSFER. */
    public TransactionPrefill resolve(FinanceBook book) {
        TransactionType resolvedType = type == null ? TransactionType.EXPENSE : type;
        String wallet = active(book, walletId) ? walletId : null;
        String destination = resolvedType == TransactionType.TRANSFER && active(book, destinationWalletId)
                && !destinationWalletId.equals(wallet) ? destinationWalletId : null;
        String category = resolvedType != TransactionType.TRANSFER && activeCategory(book, categoryId, resolvedType) ? categoryId : null;
        return new TransactionPrefill(resolvedType, wallet, destination, amountMinor, category);
    }

    private static boolean active(FinanceBook book, String id) {
        if (id == null || book == null) return false;
        Wallet wallet = book.wallet(id);
        return wallet != null && wallet.archivedAt == null;
    }

    private static boolean activeCategory(FinanceBook book, String id, TransactionType expectedType) {
        if (id == null || book == null) return false;
        Category category = book.category(id);
        return category != null && category.archivedAt == null && category.type == expectedType;
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
