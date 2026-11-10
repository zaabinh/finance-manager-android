package com.example.fintrack.app.setup.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PendingWalletListTest {
    @Test public void cashIsFirstAndReplaced() {
        PendingWalletList list = new PendingWalletList();
        assertFalse(list.hasCash());
        list.add(new PendingWallet("Vietcombank", "BANK", 5_000, true, 1));
        list.setCash(100_000);
        assertTrue(list.hasCash());
        assertEquals(100_000, list.cashBalance());
        assertEquals(PendingWalletList.CASH_NAME, list.all().get(0).name);
        list.setCash(250_000);
        assertEquals(2, list.size());
        assertEquals(250_000, list.cashBalance());
        assertEquals(1, list.others().size());
        assertEquals("Vietcombank", list.others().get(0).name);
    }
    @Test public void othersWithoutCash() {
        PendingWalletList list = new PendingWalletList();
        list.add(new PendingWallet("A", "BANK", 0, true, 0));
        assertFalse(list.hasCash());
        assertEquals(1, list.others().size());
    }
}
