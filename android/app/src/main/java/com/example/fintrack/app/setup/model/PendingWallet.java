package com.example.fintrack.app.setup.model;

import java.io.Serializable;

/** A wallet collected during first setup; not sent to the backend yet. */
public final class PendingWallet implements Serializable {
    private static final long serialVersionUID = 1L;
    public final String name;
    public final String walletType; // FinanceBook.WalletType name, e.g. CASH
    public final long openingBalance;
    public final boolean includeInAssets;
    public final int sortOrder;

    public PendingWallet(String name, String walletType, long openingBalance, boolean includeInAssets, int sortOrder) {
        this.name = name; this.walletType = walletType; this.openingBalance = openingBalance;
        this.includeInAssets = includeInAssets; this.sortOrder = sortOrder;
    }
}
