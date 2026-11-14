package com.example.fintrack.app.finance.domain;

/** Immutable finance entity. */
public final class Wallet {
    public final String id;
    public final String name;
    public final long openingBalance;
    public final WalletType walletType;
    public final String archivedAt;
    public final boolean includeInAssets;
    public final int sortOrder;
    public final Long serverBalanceMinor;
    public Wallet(String id, String name, long openingBalance, WalletType walletType, String archivedAt, boolean includeInAssets, int sortOrder, Long serverBalanceMinor) {
        this.id = id;
        this.name = name;
        this.openingBalance = openingBalance;
        this.walletType = walletType;
        this.archivedAt = archivedAt;
        this.includeInAssets = includeInAssets;
        this.sortOrder = sortOrder;
        this.serverBalanceMinor = serverBalanceMinor;
    }
    public Wallet(String id, String name, long openingBalance) { this(id, name, openingBalance, WalletType.CASH, null, true, 0, null); }
    public Wallet withArchivedAt(String value) {
        return new Wallet(id, name, openingBalance, walletType, value, includeInAssets, sortOrder, serverBalanceMinor);
    }
    public Wallet withWalletType(WalletType value) {
        return new Wallet(id, name, openingBalance, value, archivedAt, includeInAssets, sortOrder, serverBalanceMinor);
    }
    public Wallet withIncludeInAssets(boolean value) {
        return new Wallet(id, name, openingBalance, walletType, archivedAt, value, sortOrder, serverBalanceMinor);
    }
}
