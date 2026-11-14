package com.example.fintrack.app.finance.data;

import com.example.fintrack.app.finance.domain.FinanceBook;

/** A committed command, independent of any later snapshot read. */
public final class MutationReceipt {
    public final String createdEntityId;
    public final FinanceBook snapshot;

    public MutationReceipt(String createdEntityId, FinanceBook snapshot) {
        this.createdEntityId = createdEntityId;
        this.snapshot = snapshot == null ? null : new FinanceBook(snapshot.snapshot());
    }
}
