package com.example.fintrack.app.wallet.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WalletDraftTest {
    @Test public void acceptsGroupedBalanceAndBlankDefaults() {
        WalletDraft d = WalletDraft.parse(" Techcombank ", "1.250.000", "");
        assertEquals(WalletDraft.Error.NONE, d.error);
        assertEquals("Techcombank", d.name);
        assertEquals(1_250_000L, d.opening);
        assertEquals(0, d.sortOrder);
        assertEquals(0L, WalletDraft.parse("A", "", null).opening);
    }
    @Test public void rejectsBadInput() {
        assertEquals(WalletDraft.Error.NAME, WalletDraft.parse("  ", "0", "0").error);
        assertEquals(WalletDraft.Error.NAME, WalletDraft.parse(new String(new char[101]).replace('\0', 'a'), "0", "0").error);
        assertEquals(WalletDraft.Error.OPENING, WalletDraft.parse("A", "12a", "0").error);
        assertEquals(WalletDraft.Error.OPENING, WalletDraft.parse("A", "1.000.000.000.000", "0").error);
        assertEquals(WalletDraft.Error.ORDER, WalletDraft.parse("A", "0", "-1").error);
    }
}
