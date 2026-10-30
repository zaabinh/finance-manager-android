package com.example.fintrack.app.finance.data;

import static org.junit.Assert.*;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.remote.FinanceApiService;
import com.example.fintrack.app.finance.remote.FinanceDtos;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.UUID;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.Test;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Uses Retrofit's real HTTP serialization with an in-process transport; no backend needed. */
public class FinanceApiContractTest {
    private Request captured;
    private FinanceApiService api(int status, String json) {
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            captured = chain.request();
            return new Response.Builder().request(captured).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
                    .body(ResponseBody.create(json, MediaType.get("application/json"))).build();
        }).build();
        return new Retrofit.Builder().baseUrl("https://contract.invalid/").client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(FinanceApiService.class);
    }
    private String body() throws IOException { Buffer buffer = new Buffer(); captured.body().writeTo(buffer); return buffer.readUtf8(); }
    @Test public void transactionCreateUsesIntegerVndUtcAndStableRequestId() throws Exception {
        FinanceBook.Transaction value = new FinanceBook.Transaction(UUID.randomUUID().toString(), FinanceBook.Type.EXPENSE,
                999_999_999_999L, "category", "wallet", null, 1790992800000L, "ghi chú", "Quán Đồng");
        FinanceAction action = FinanceAction.transaction(value, false);
        FinanceApiService api = api(201, "{\"transactionId\":\"created\"}"); action.remote.send(api);
        assertEquals("POST", captured.method()); assertEquals("/api/v1/transactions", captured.url().encodedPath());
        com.google.gson.JsonObject json = new Gson().fromJson(body(), com.google.gson.JsonObject.class);
        assertEquals(999_999_999_999L, json.get("amountMinor").getAsLong());
        assertEquals(value.id, json.get("clientRequestId").getAsString());
        assertEquals(value.occurredAt, FinanceDtos.parseTimestamp(json.get("occurredAt").getAsString()));
        action.remote.send(api); assertEquals(value.id, new Gson().fromJson(body(), com.google.gson.JsonObject.class).get("clientRequestId").getAsString());
    }
    @Test public void transferOmitsCategoryAndIncludesBothWallets() throws Exception {
        FinanceBook.Transaction value = new FinanceBook.Transaction("id", FinanceBook.Type.TRANSFER, 50_000, "ignored", "from", "to", 0, "", "");
        FinanceAction.transaction(value, true).remote.send(api(200, "{\"transactionId\":\"id\"}"));
        assertEquals("PUT", captured.method()); assertEquals("/api/v1/transactions/id", captured.url().encodedPath());
        com.google.gson.JsonObject json = new Gson().fromJson(body(), com.google.gson.JsonObject.class);
        assertFalse(json.has("categoryId")); assertEquals("from", json.get("walletId").getAsString()); assertEquals("to", json.get("destinationWalletId").getAsString());
    }
    @Test public void budgetRequestUsesMonthAndLimitAndSupports204Delete() throws Exception {
        FinanceAction.budget(new FinanceBook.Budget("b", "food", "2026-10", 1_500_000), true).remote.send(api(200, "{\"budgetId\":\"b\"}"));
        assertEquals("PUT", captured.method());
        assertEquals("2026-10", new Gson().fromJson(body(), com.google.gson.JsonObject.class).get("month").getAsString());
        assertEquals(1_500_000, new Gson().fromJson(body(), com.google.gson.JsonObject.class).get("limitMinor").getAsLong());
        FinanceAction.deleteBudget("b").remote.send(api(204, "")); assertEquals("DELETE", captured.method());
        assertEquals("/api/v1/budgets/b", captured.url().encodedPath());
    }
    @Test public void listUsesExplicitPaginationAndDirectDtoBodies() {
        FinanceDtos.TransactionPage page = FinanceAction.required(api(200, "{\"items\":[],\"page\":2,\"totalPages\":3}").transactions(2, 200));
        assertEquals(2, page.page); assertTrue(page.items.isEmpty());
        assertEquals("2", captured.url().queryParameter("page")); assertEquals("200", captured.url().queryParameter("size"));
    }
    @Test public void httpErrorsKeepTheirMeaningAndDoNotBecomeMockSuccess() {
        int[] statuses = {401, 404, 409, 422, 500};
        FinanceBook.Problem[] problems = {FinanceBook.Problem.UNAUTHORIZED, FinanceBook.Problem.NOT_FOUND,
                FinanceBook.Problem.CONFLICT, FinanceBook.Problem.VALIDATION, FinanceBook.Problem.SERVER};
        for (int i = 0; i < statuses.length; i++) {
            FinanceApiService service = api(statuses[i], "{\"message\":\"fixture\"}");
            assertEquals(problems[i], assertThrows(FinanceBook.Failure.class, () -> FinanceAction.required(service.wallets())).problem);
        }
    }
    @Test public void disconnectedTransportIsAnError() {
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> { throw new IOException("offline"); }).build();
        FinanceApiService api = new Retrofit.Builder().baseUrl("https://contract.invalid/").client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(FinanceApiService.class);
        assertEquals(FinanceBook.Problem.NETWORK, assertThrows(FinanceBook.Failure.class, () -> FinanceAction.required(api.wallets())).problem);
    }
    @Test public void timestampsAcceptServerPrecisionAndRejectInvalidDates() {
        assertEquals(FinanceDtos.parseTimestamp("2026-10-31T17:00:00Z"), FinanceDtos.parseTimestamp("2026-10-31T17:00:00.000000Z"));
        assertEquals("2026-11", FinanceBook.month(FinanceDtos.parseTimestamp("2026-10-31T17:00:00Z")));
        assertThrows(FinanceBook.Failure.class, () -> FinanceDtos.parseTimestamp("2026-02-30T17:00:00Z"));
        assertThrows(FinanceBook.Failure.class, () -> FinanceDtos.parseTimestamp("2026-10-31"));
    }
    @Test public void helperRequestsFollowWalletAndCategoryVocabulary() throws Exception {
        FinanceAction.wallet("Tiền mặt", 20_000, FinanceBook.WalletType.CASH).remote.send(api(201, "{\"walletId\":\"w\"}"));
        assertEquals("/api/v1/wallets", captured.url().encodedPath());
        assertEquals("CASH", new Gson().fromJson(body(), com.google.gson.JsonObject.class).get("walletType").getAsString());
        FinanceAction.category("Ăn uống", FinanceBook.Type.EXPENSE).remote.send(api(201, "{\"categoryId\":\"c\"}"));
        assertEquals("/api/v1/categories", captured.url().encodedPath());
    }
}
