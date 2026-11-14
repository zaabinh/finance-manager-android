package com.example.fintrack.app.finance.domain;

/** Immutable finance entity. */
public final class Transaction {
    public final String id;
    public final TransactionType type;
    public final long amount;
    public final String categoryId;
    public final String walletId;
    public final String destinationWalletId;
    public final long occurredAt;
    public final String note;
    public final String payee;
    public final String status;
    public final String voidedAt;
    public final String clientRequestId;
    public Transaction(String id, TransactionType type, long amount, String categoryId, String walletId, String destinationWalletId, long occurredAt, String note, String payee, String status, String voidedAt, String clientRequestId) {
        this.id = id;
        this.type = type;
        this.amount = amount;
        this.categoryId = categoryId;
        this.walletId = walletId;
        this.destinationWalletId = destinationWalletId;
        this.occurredAt = occurredAt;
        this.note = note;
        this.payee = payee;
        this.status = status;
        this.voidedAt = voidedAt;
        this.clientRequestId = clientRequestId;
    }
    public Transaction(String id, TransactionType type, long amount, String categoryId, String walletId, String destinationWalletId, long occurredAt, String note, String payee) { this(id, type, amount, categoryId, walletId, destinationWalletId, occurredAt, note, payee, "POSTED", null, null); }
    public Transaction withClientRequestId(String value) {
        return new Transaction(id, type, amount, categoryId, walletId, destinationWalletId, occurredAt, note, payee, status, voidedAt, value);
    }
}
