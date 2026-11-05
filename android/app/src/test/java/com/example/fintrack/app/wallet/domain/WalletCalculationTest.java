package com.example.fintrack.app.wallet.domain;

import static org.junit.Assert.assertEquals;

import com.example.fintrack.app.finance.domain.FinanceBook;

import org.junit.Test;

public class WalletCalculationTest {

    @Test
    public void testWalletCreation() {
        FinanceBook.Wallet wallet = new FinanceBook.Wallet("w1", "Ví chính", 1_000_000);
        assertEquals("w1", wallet.id);
        assertEquals("Ví chính", wallet.name);
        assertEquals(1_000_000, wallet.openingBalance);
        assertEquals(FinanceBook.WalletType.CASH, wallet.walletType);
    }

    @Test
    public void testAssetsCalculation() {
        FinanceBook.State state = FinanceBook.empty();
        state.wallets.add(new FinanceBook.Wallet("w1", "Ví chính", 5_000_000));
        FinanceBook.Wallet savings = new FinanceBook.Wallet("w2", "Tiết kiệm", 10_000_000);
        savings.walletType = FinanceBook.WalletType.SAVINGS;
        state.wallets.add(savings);

        FinanceBook book = new FinanceBook(state);
        assertEquals(15_000_000, book.assets(false));
        assertEquals(10_000_000, book.assets(true));
    }
}
