package com.example.fintrack.app.transaction.remote;

import static org.junit.Assert.*;

import com.example.fintrack.app.finance.domain.FinanceBook;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.HttpUrl;
import okio.Buffer;
import org.junit.Test;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Real Retrofit request and Gson DTO mapping, without a network dependency. */
public class TransactionApiContractTest {
    private final Gson json = new Gson();
    private final TransactionApiService api = new Retrofit.Builder().baseUrl("https://finance.test/")
            .addConverterFactory(GsonConverterFactory.create(json)).build().create(TransactionApiService.class);

    @Test public void historyPassesPagingFiltersAndLiteralSearchToServer() {
        HttpUrl url = api.transactions(3, 50, "2026-11-01", "2026-11-30", "wallet", "category",
                FinanceBook.Type.EXPENSE, "50%_ off", "amountMinor,asc").request().url();
        assertEquals("/api/v1/transactions", url.encodedPath());
        assertEquals("3", url.queryParameter("page"));
        assertEquals("2026-11-30", url.queryParameter("dateTo"));
        assertEquals("wallet", url.queryParameter("walletId"));
        assertEquals("category", url.queryParameter("categoryId"));
        assertEquals("EXPENSE", url.queryParameter("type"));
        assertEquals("50%_ off", url.queryParameter("q"));
        assertEquals("amountMinor,asc", url.queryParameter("sort"));
        assertEquals("/api/v1/transactions/not-on-current-page", api.transaction("not-on-current-page").request().url().encodedPath());
        assertNull(api.transactions(0, 50, null, null, null, null, null, null, null).request().url().queryParameter("dateFrom"));
    }

    @Test public void postKeepsStableKeyAndPutOmitsItAndMoneyIsInteger() throws Exception {
        FinanceBook.Transaction value = new FinanceBook.Transaction("local", FinanceBook.Type.EXPENSE, 999_999_999_999L,
                "category", "wallet", null, 1_793_522_700_123L, "note", "payee");
        value.clientRequestId = "stable-key";
        Buffer post = new Buffer(); api.addTransaction(new TransactionRequest(value, false)).request().body().writeTo(post);
        JsonObject created = json.fromJson(post.readUtf8(), JsonObject.class);
        assertEquals("stable-key", created.get("clientRequestId").getAsString());
        assertEquals("999999999999", created.get("amountMinor").toString());
        assertTrue(created.get("occurredAt").getAsString().endsWith("Z"));
        assertFalse(created.has("destinationWalletId"));
        Buffer put = new Buffer(); api.updateTransaction("server-id", new TransactionRequest(value, true)).request().body().writeTo(put);
        assertFalse(json.fromJson(put.readUtf8(), JsonObject.class).has("clientRequestId"));
    }

    @Test public void voidedReplayRemainsVoidedAndUtcFractionsRoundTrip() {
        TransactionResponse response = json.fromJson("{\"transactionId\":\"id\",\"transactionType\":\"INCOME\",\"amountMinor\":5,"
                + "\"occurredAt\":\"2026-11-07T08:45:00.123456Z\",\"status\":\"VOIDED\",\"voidedAt\":\"2026-11-08T00:00:00Z\"}", TransactionResponse.class);
        FinanceBook.Transaction domain = response.domain();
        assertEquals("VOIDED", domain.status);
        assertEquals("2026-11-08T00:00:00Z", domain.voidedAt);
        assertEquals("2026-11-07T08:45:00.123Z", TransactionResponse.timestamp(domain.occurredAt));
        assertEquals("", domain.note);
        assertEquals("", domain.payee);
        for (String malformed : new String[]{"2026-11-07", "2026-11-07T08:45:00", "2026-02-30T08:45:00Z", "garbage"}) {
            try { TransactionResponse.parseTimestamp(malformed); fail("Accepted " + malformed); }
            catch (FinanceBook.Failure expected) { assertEquals(FinanceBook.Problem.SERVER, expected.problem); }
        }
    }
}
