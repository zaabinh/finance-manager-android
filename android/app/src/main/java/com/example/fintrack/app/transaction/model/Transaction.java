package com.example.fintrack.app.transaction.model;

import com.example.fintrack.app.category.model.Category;

public final class Transaction {
    public String id, categoryId, walletId, destinationWalletId, note, payee;
    public Category.Type type;
    public long amount, occurredAt;

    public Transaction(String id, Category.Type type, long amount, String categoryId, String walletId,
                       String destinationWalletId, long occurredAt, String note, String payee) {
        this.id = id;
        this.type = type;
        this.amount = amount;
        this.categoryId = categoryId;
        this.walletId = walletId;
        this.destinationWalletId = destinationWalletId;
        this.occurredAt = occurredAt;
        this.note = note;
        this.payee = payee;
    }
}
