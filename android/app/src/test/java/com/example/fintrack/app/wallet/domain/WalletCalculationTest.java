package com.example.fintrack.app.wallet.domain;

import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.FinanceSnapshot;
import com.example.fintrack.app.finance.domain.Wallet;
import com.example.fintrack.app.finance.domain.WalletType;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WalletCalculationTest {

    @Test
    public void testWalletCreation() {
        Wallet wallet = new Wallet("w1", "Ví chính", 1_000_000);
        assertEquals("w1", wallet.id);
        assertEquals("Ví chính", wallet.name);
        assertEquals(1_000_000, wallet.openingBalance);
        assertEquals(WalletType.CASH, wallet.walletType);
    }

    @Test
    public void testAssetsCalculation() {
        FinanceSnapshot.Builder state = FinanceBook.empty();
        state.wallets.add(new Wallet("w1", "Ví chính", 5_000_000));
        Wallet savings = new Wallet("w2", "Tiết kiệm", 10_000_000).withWalletType(WalletType.SAVINGS);
        state.wallets.add(savings);

        FinanceBook book = new FinanceBook(state);
        assertEquals(15_000_000, book.assets(false));
        assertEquals(10_000_000, book.assets(true));
    }
}
