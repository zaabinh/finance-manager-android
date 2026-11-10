package com.example.fintrack.app.wallet.domain;

import static org.junit.Assert.*;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.wallet.remote.WalletRequest;
import com.example.fintrack.app.wallet.remote.WalletResponse;
import com.example.fintrack.app.wallet.remote.WalletStatusRequest;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;

public class WalletContractTest {
    private final Gson gson = new Gson();

    @Test public void responseSeparatesOpeningAndCurrentBalanceAndKeepsLifecycle() {
        WalletResponse response = gson.fromJson("{\"walletId\":\"wallet-uuid\",\"name\":\"Wallet\","
                + "\"walletType\":\"BANK\",\"openingBalanceMinor\":999999999999,\"balanceMinor\":999999900000,"
                + "\"includeInAssets\":false,\"sortOrder\":7,\"archivedAt\":\"2026-11-07T00:00:00Z\"}", WalletResponse.class);
        FinanceBook.Wallet wallet = response.domain();
        assertEquals(999_999_999_999L, wallet.openingBalance);
        assertEquals(Long.valueOf(999_999_900_000L), wallet.serverBalanceMinor);
        assertFalse(wallet.includeInAssets);
        assertEquals(7, wallet.sortOrder);
        assertNotNull(wallet.archivedAt);
        assertEquals(FinanceBook.WalletType.BANK, wallet.walletType);
    }

    @Test public void requestsOnlyWriteContractFieldsWithIntegerVnd() {
        JsonObject request = gson.toJsonTree(new WalletRequest("Wallet", 999_999_999_999L,
                FinanceBook.WalletType.SAVINGS, false, 4)).getAsJsonObject();
        assertEquals(5, request.size());
        assertEquals("999999999999", request.get("openingBalanceMinor").toString());
        assertFalse(request.has("balanceMinor"));
        assertFalse(request.has("userId"));
        assertFalse(request.has("provider"));
        assertEquals("{\"active\":true}", gson.toJson(new WalletStatusRequest(true)));
    }
}
