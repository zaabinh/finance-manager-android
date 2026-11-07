package com.example.fintrack.app.finance.data;

import static org.junit.Assert.*;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.remote.FinanceApiBundle;
import com.example.fintrack.app.wallet.remote.WalletApiService;
import com.example.fintrack.app.category.remote.CategoryApiService;
import com.example.fintrack.app.budget.remote.BudgetApiService;
import com.example.fintrack.app.transaction.remote.TransactionApiService;
import com.example.fintrack.app.transaction.remote.TransactionResponse;
import com.example.fintrack.app.transaction.remote.TransactionPageResponse;
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
    private FinanceApiBundle apiBundle(int status, String json) {
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            captured = chain.request();
            return new Response.Builder().request(captured).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
                    .body(ResponseBody.create(json, MediaType.get("application/json"))).build();
        }).build();
        Retrofit retrofit = new Retrofit.Builder().baseUrl("https://contract.invalid/").client(client)
                .addConverterFactory(GsonConverterFactory.create()).build();
        return new FinanceApiBundle(
                retrofit.create(WalletApiService.class),
                retrofit.create(CategoryApiService.class),
                retrofit.create(BudgetApiService.class),
                retrofit.create(TransactionApiService.class)
        );
    }
    private String body() throws IOException { Buffer buffer = new Buffer(); captured.body().writeTo(buffer); return buffer.readUtf8(); }
    @Test public void transactionCreateUsesIntegerVndUtcAndStableRequestId() throws Exception {
        FinanceBook.Transaction value = new FinanceBook.Transaction(UUID.randomUUID().toString(), FinanceBook.Type.EXPENSE,
                999_999_999_999L, "category", "wallet", null, 1790992800000L, "ghi chú", "Quán Đồng");
        FinanceAction action = FinanceAction.transaction(value, false);
        FinanceApiBundle api = apiBundle(201, "{\"transactionId\":\"created\"}"); action.remote.send(api);
        assertEquals("POST", captured.method()); assertEquals("/api/v1/transactions", captured.url().encodedPath());
        com.google.gson.JsonObject json = new Gson().fromJson(body(), com.google.gson.JsonObject.class);
        assertEquals(999_999_999_999L, json.get("amountMinor").getAsLong());
        assertEquals(value.id, json.get("clientRequestId").getAsString());
        assertEquals(value.occurredAt, TransactionResponse.parseTimestamp(json.get("occurredAt").getAsString()));
        action.remote.send(api); assertEquals(value.id, new Gson().fromJson(body(), com.google.gson.JsonObject.class).get("clientRequestId").getAsString());
    }
    // Was transferOmitsCategoryAndIncludesBothWallets: TRANSFER is out of scope (contract section 5), so it is never sent.
    @Test public void transferIsRejectedBeforeAnyRequestAndInMock() {
        FinanceBook.Transaction value = new FinanceBook.Transaction("id", FinanceBook.Type.TRANSFER, 50_000, null, "from", "to", 0, "", "");
        FinanceApiBundle api = apiBundle(200, "{\"transactionId\":\"id\"}");
        for (boolean editing : new boolean[]{false, true}) {
            FinanceBook.Failure failure = assertThrows(FinanceBook.Failure.class, () -> FinanceAction.transaction(value, editing).remote.send(api));
            assertEquals("TRANSACTION_TYPE_UNSUPPORTED", failure.code);
        }
        assertNull(captured);
        FinanceBook book = new FinanceBook(FinanceBook.empty());
        String cash = book.addWallet("Tiền mặt", 100_000).id, bank = book.addWallet("Ngân hàng", 0).id;
        FinanceBook.Transaction mock = new FinanceBook.Transaction("t", FinanceBook.Type.TRANSFER, 1_000, null, cash, bank, 0, "", "");
        assertEquals(FinanceBook.Problem.VALIDATION, assertThrows(FinanceBook.Failure.class,
                () -> FinanceAction.transaction(mock, false).local.accept(book)).problem);
        assertTrue(book.state().transactions.isEmpty());
    }
    @Test public void postUsesStableClientRequestIdSeparateFromIdAndPutOmitsIt() throws Exception {
        FinanceBook.Transaction value = new FinanceBook.Transaction("local-id", FinanceBook.Type.INCOME, 1_000, "c", "w", "ignored", 0, "", "");
        value.clientRequestId = "3bef66f9-8969-447e-b0ea-aa94dd3422f3";
        FinanceApiBundle api = apiBundle(201, "{\"transactionId\":\"server-id\",\"status\":\"POSTED\"}");
        FinanceAction action = FinanceAction.transaction(value, false);
        action.remote.send(api);
        com.google.gson.JsonObject first = new Gson().fromJson(body(), com.google.gson.JsonObject.class);
        action.remote.send(api); // timeout retry reuses the same action/key
        com.google.gson.JsonObject retry = new Gson().fromJson(body(), com.google.gson.JsonObject.class);
        assertEquals(value.clientRequestId, first.get("clientRequestId").getAsString());
        assertEquals(first, retry); assertFalse(first.has("destinationWalletId"));
        FinanceAction.transaction(value, true).remote.send(api);
        assertEquals("/api/v1/transactions/local-id", captured.url().encodedPath());
        assertFalse(new Gson().fromJson(body(), com.google.gson.JsonObject.class).has("clientRequestId"));
    }
    @Test public void replayedVoidedCreateIsNotReportedAsCreated() {
        FinanceBook.Transaction value = new FinanceBook.Transaction("id", FinanceBook.Type.EXPENSE, 1_000, "c", "w", null, 0, "", "");
        FinanceBook.Failure failure = assertThrows(FinanceBook.Failure.class, () -> FinanceAction.transaction(value, false).remote
                .send(apiBundle(200, "{\"transactionId\":\"id\",\"status\":\"VOIDED\",\"voidedAt\":\"2026-11-07T08:45:00Z\"}")));
        assertEquals(FinanceBook.Problem.VOIDED, failure.problem); assertEquals("TRANSACTION_REPLAY_VOIDED", failure.code);
    }
    @Test public void transactionErrorCodesAndFieldErrorsAreKeptForLocalisation() {
        FinanceBook.Failure funds = assertThrows(FinanceBook.Failure.class, () -> FinanceAction.deleteTransaction("id").remote.send(apiBundle(409,
                "{\"status\":409,\"errorCode\":\"WALLET_INSUFFICIENT_FUNDS\",\"message\":\"Wallet would be negative\"}")));
        assertEquals(FinanceBook.Problem.INSUFFICIENT_FUNDS, funds.problem); assertNull(funds.userMessage);
        FinanceBook.Failure voided = assertThrows(FinanceBook.Failure.class, () -> FinanceAction.transaction(new FinanceBook.Transaction("id",
                FinanceBook.Type.EXPENSE, 1, "c", "w", null, 0, "", ""), true).remote.send(apiBundle(409, "{\"errorCode\":\"TRANSACTION_VOIDED\"}")));
        assertEquals(FinanceBook.Problem.VOIDED, voided.problem);
        FinanceBook.Failure fields = assertThrows(FinanceBook.Failure.class, () -> FinanceAction.deleteTransaction("id").remote.send(apiBundle(422,
                "{\"errorCode\":\"VALIDATION_ERROR\",\"fieldErrors\":[{\"field\":\"amountMinor\",\"message\":\"must be positive\"}]}")));
        assertEquals(FinanceBook.Problem.VALIDATION, fields.problem); assertEquals(java.util.Collections.singletonList("amountMinor"), fields.fields);
        FinanceBook.Failure missing = assertThrows(FinanceBook.Failure.class, () -> FinanceAction.deleteTransaction("id").remote.send(apiBundle(404,
                "{\"errorCode\":\"NOT_FOUND\",\"message\":\"Not found\"}")));
        assertEquals(FinanceBook.Problem.NOT_FOUND, missing.problem); assertNull(missing.userMessage);
    }
    @Test public void detailResponseCarriesLifecycleFields() {
        TransactionResponse response = new Gson().fromJson("{\"transactionId\":\"t\",\"transactionType\":\"EXPENSE\",\"amountMinor\":95000,"
                + "\"occurredAt\":\"2026-11-07T08:45:00Z\",\"status\":\"VOIDED\",\"voidedAt\":\"2026-11-08T00:00:00Z\","
                + "\"createdAt\":\"2026-11-07T08:45:01Z\",\"updatedAt\":\"2026-11-08T00:00:00Z\",\"clientRequestId\":\"k\"}", TransactionResponse.class);
        assertEquals("2026-11-07T08:45:01Z", response.createdAt); assertEquals("2026-11-08T00:00:00Z", response.updatedAt);
        FinanceBook.Transaction value = response.domain();
        assertEquals("VOIDED", value.status); assertEquals("k", value.clientRequestId); assertEquals(95_000, value.amount);
    }
    @Test public void budgetRequestUsesMonthAndLimitAndSupports204Delete() throws Exception {
        FinanceAction.budget(new FinanceBook.Budget("b", "food", "2026-10", 1_500_000), true).remote.send(apiBundle(200, "{\"budgetId\":\"b\"}"));
        assertEquals("PUT", captured.method());
        assertEquals("2026-10", new Gson().fromJson(body(), com.google.gson.JsonObject.class).get("month").getAsString());
        assertEquals(1_500_000, new Gson().fromJson(body(), com.google.gson.JsonObject.class).get("limitMinor").getAsLong());
        FinanceAction.deleteBudget("b").remote.send(apiBundle(204, "")); assertEquals("DELETE", captured.method());
        assertEquals("/api/v1/budgets/b", captured.url().encodedPath());
    }
    @Test public void listUsesExplicitPaginationAndDirectDtoBodies() {
        FinanceApiBundle bundle = apiBundle(200, "{\"items\":[],\"page\":2,\"totalPages\":3}");
        TransactionPageResponse page = FinanceAction.required(bundle.transactions.transactions(2, 200, null, null, null, null, null, null, null));
        assertEquals(2, page.page); assertTrue(page.items.isEmpty());
        assertEquals("2", captured.url().queryParameter("page")); assertEquals("200", captured.url().queryParameter("size"));
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList("page", "size")), captured.url().queryParameterNames()); // null filters are omitted
    }
    @Test public void historyQuerySendsContractFilters() {
        FinanceBook.Query query = FinanceBook.Query.month("2026-02", FinanceBook.Type.INCOME, "w", "c", "  50% off ");
        FinanceApiBundle bundle = apiBundle(200, "{\"items\":[],\"page\":0,\"totalPages\":0}");
        FinanceAction.required(bundle.transactions.transactions(0, 50, query.dateFrom, query.dateTo, query.walletId, query.categoryId,
                query.type, query.q, query.sort));
        okhttp3.HttpUrl url = captured.url();
        assertEquals("2026-02-01", url.queryParameter("dateFrom")); assertEquals("2026-02-28", url.queryParameter("dateTo"));
        assertEquals("INCOME", url.queryParameter("type")); assertEquals("w", url.queryParameter("walletId"));
        assertEquals("c", url.queryParameter("categoryId")); assertEquals("50% off", url.queryParameter("q"));
        assertEquals("occurredAt,desc", url.queryParameter("sort"));
    }
    @Test public void httpErrorsKeepTheirMeaningAndDoNotBecomeMockSuccess() {
        int[] statuses = {401, 404, 409, 422, 500};
        FinanceBook.Problem[] problems = {FinanceBook.Problem.UNAUTHORIZED, FinanceBook.Problem.NOT_FOUND,
                FinanceBook.Problem.CONFLICT, FinanceBook.Problem.VALIDATION, FinanceBook.Problem.SERVER};
        for (int i = 0; i < statuses.length; i++) {
            FinanceApiBundle bundle = apiBundle(statuses[i], "{\"message\":\"fixture\"}");
            assertEquals(problems[i], assertThrows(FinanceBook.Failure.class, () -> FinanceAction.required(bundle.wallets.wallets())).problem);
        }
    }
    @Test public void disconnectedTransportIsAnError() {
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> { throw new IOException("offline"); }).build();
        Retrofit retrofit = new Retrofit.Builder().baseUrl("https://contract.invalid/").client(client)
                .addConverterFactory(GsonConverterFactory.create()).build();
        FinanceApiBundle api = new FinanceApiBundle(
                retrofit.create(WalletApiService.class),
                retrofit.create(CategoryApiService.class),
                retrofit.create(BudgetApiService.class),
                retrofit.create(TransactionApiService.class)
        );
        assertEquals(FinanceBook.Problem.NETWORK, assertThrows(FinanceBook.Failure.class, () -> FinanceAction.required(api.wallets.wallets())).problem);
    }
    @Test public void timestampsAcceptServerPrecisionAndRejectInvalidDates() {
        assertEquals(TransactionResponse.parseTimestamp("2026-10-31T17:00:00Z"), TransactionResponse.parseTimestamp("2026-10-31T17:00:00.000000Z"));
        assertEquals("2026-11", FinanceBook.month(TransactionResponse.parseTimestamp("2026-10-31T17:00:00Z")));
        assertThrows(FinanceBook.Failure.class, () -> TransactionResponse.parseTimestamp("2026-02-30T17:00:00Z"));
        assertThrows(FinanceBook.Failure.class, () -> TransactionResponse.parseTimestamp("2026-10-31"));
    }
    @Test public void helperRequestsFollowWalletAndCategoryVocabulary() throws Exception {
        FinanceAction.wallet("Tiền mặt", 20_000, FinanceBook.WalletType.CASH).remote.send(apiBundle(201, "{\"walletId\":\"w\"}"));
        assertEquals("/api/v1/wallets", captured.url().encodedPath());
        assertEquals("CASH", new Gson().fromJson(body(), com.google.gson.JsonObject.class).get("walletType").getAsString());
        FinanceAction.category("Ăn uống", FinanceBook.Type.EXPENSE).remote.send(apiBundle(201, "{\"categoryId\":\"c\"}"));
        assertEquals("/api/v1/categories", captured.url().encodedPath());
    }
}
