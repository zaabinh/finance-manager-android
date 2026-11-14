package com.example.fintrack.app.finance.domain;

import java.util.HashSet;
import java.util.Set;

/** Transaction rules over immutable values; all ledger lookups and writes stay in FinanceBook. */
final class TransactionPolicy {
    private TransactionPolicy() { }

    static Transaction normalize(Transaction value, Wallet source, Wallet destination, Category category, boolean restoring) {
        if (value == null || value.id == null || value.id.isEmpty() || value.type == null) throw new FinanceFailure(FinanceProblem.NOT_FOUND);
        FinanceBook.amount(value.amount);
        if (value.occurredAt < 0 || value.occurredAt > 253402214400000L) throw new FinanceFailure(FinanceProblem.DATE);
        if (source == null) throw new FinanceFailure(FinanceProblem.WALLET);
        if (!restoring && source.archivedAt != null) throw new FinanceFailure(FinanceProblem.ARCHIVED);
        if (value.type == TransactionType.TRANSFER) {
            if (destination == null) throw new FinanceFailure(FinanceProblem.WALLET);
            if (value.walletId.equals(value.destinationWalletId)) throw new FinanceFailure(FinanceProblem.SAME_WALLET);
            if (!restoring && destination.archivedAt != null) throw new FinanceFailure(FinanceProblem.ARCHIVED);
        } else {
            if (category == null || category.type != value.type) throw new FinanceFailure(FinanceProblem.CATEGORY);
            if (!restoring && category.archivedAt != null) throw new FinanceFailure(FinanceProblem.ARCHIVED);
        }
        Transaction normalized = new Transaction(value.id, value.type, value.amount,
                value.type == TransactionType.TRANSFER ? null : value.categoryId, value.walletId,
                value.type == TransactionType.TRANSFER ? value.destinationWalletId : null, value.occurredAt,
                value.note == null ? "" : value.note.trim(), value.payee == null ? "" : value.payee.trim(),
                value.status, value.voidedAt, value.clientRequestId);
        if (normalized.note.length() > 2000 || normalized.payee.length() > 160) throw new FinanceFailure(FinanceProblem.NAME);
        return normalized;
    }

    static void validateReplacement(Transaction old, boolean editing, boolean restoring) {
        if ((editing && old == null) || (!editing && old != null)) throw new FinanceFailure(FinanceProblem.NOT_FOUND);
        if (!restoring && old != null && "VOIDED".equals(old.status)) throw new FinanceFailure(FinanceProblem.VOIDED);
    }

    static Set<String> affectedWalletIds(Transaction old, Transaction value) {
        Set<String> ids = new HashSet<>();
        if (old != null) { ids.add(old.walletId); if (old.destinationWalletId != null) ids.add(old.destinationWalletId); }
        if (value != null) { ids.add(value.walletId); if (value.destinationWalletId != null) ids.add(value.destinationWalletId); }
        return ids;
    }

    static long contribution(Transaction value, String id) {
        if (value == null || "VOIDED".equals(value.status)) return 0;
        long delta = id.equals(value.walletId) ? (value.type == TransactionType.INCOME ? value.amount : -value.amount) : 0;
        if (value.type == TransactionType.TRANSFER && id.equals(value.destinationWalletId)) delta = Math.addExact(delta, value.amount);
        return delta;
    }
}
