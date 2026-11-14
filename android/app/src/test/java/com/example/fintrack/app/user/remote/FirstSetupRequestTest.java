package com.example.fintrack.app.user.remote;

import static org.junit.Assert.*;

import com.example.fintrack.app.finance.domain.WalletType;
import com.example.fintrack.app.wallet.remote.WalletRequest;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.Test;

/** The backend's strict wallet JSON rejects unknown fields, so first-setup wallets must serialize to exactly these keys. */
public class FirstSetupRequestTest {
    @Test public void walletsAreSentWithTheWalletApiFields() {
        FirstSetupRequest request = new FirstSetupRequest(null, null,
                Arrays.asList(new WalletRequest("Tiền mặt", 500_000, WalletType.CASH, true, 0)));
        JsonObject wallet = new Gson().toJsonTree(request).getAsJsonObject().getAsJsonArray("wallets").get(0).getAsJsonObject();
        assertEquals(new HashSet<>(Arrays.asList("name", "walletType", "openingBalanceMinor", "includeInAssets", "sortOrder")),
                wallet.keySet());
        assertEquals("CASH", wallet.get("walletType").getAsString());
        assertEquals(500_000, wallet.get("openingBalanceMinor").getAsLong());
    }
}
