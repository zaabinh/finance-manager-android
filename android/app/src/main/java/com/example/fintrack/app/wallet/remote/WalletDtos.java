package com.example.fintrack.app.wallet.remote;

import com.example.fintrack.app.finance.domain.FinanceBook;

public final class WalletDtos {

    public static final class Wallet {
        public String walletId, name;
        public FinanceBook.WalletType walletType;
        public long openingBalanceMinor;

        public FinanceBook.Wallet domain() {
            FinanceBook.Wallet wallet = new FinanceBook.Wallet(walletId, name, openingBalanceMinor);
            wallet.walletType = walletType;
            return wallet;
        }
    }

    public static final class WalletRequest {
        public final String name;
        public final FinanceBook.WalletType walletType;
        public final long openingBalanceMinor;

        public WalletRequest(String name, long amount, FinanceBook.WalletType type) {
            this.name = name;
            openingBalanceMinor = amount;
            walletType = type;
        }
    }

    private WalletDtos() { }
}
