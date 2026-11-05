package com.example.fintrack.app.finance.domain;

public final class Wallet {
    public enum WalletType { CASH, BANK, SAVINGS }

    public String id, name;
    public long openingBalance;
    public WalletType walletType = WalletType.CASH;

    public Wallet(String id, String name, long openingBalance) {
        this.id = id;
        this.name = name;
        this.openingBalance = openingBalance;
    }
}
