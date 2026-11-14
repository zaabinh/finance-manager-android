package com.example.fintrack.app.finance.data;

import com.example.fintrack.app.finance.ui.FinanceErrors;

import com.example.fintrack.app.core.error.AppFailure;

import com.example.fintrack.app.finance.domain.Budget;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.FinanceFailure;
import com.example.fintrack.app.finance.domain.FinanceProblem;
import com.example.fintrack.app.finance.domain.Transaction;
import com.example.fintrack.app.finance.domain.TransactionType;
import com.example.fintrack.app.finance.domain.WalletType;

import static org.junit.Assert.*;
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
    private static FinanceFailure financeFailure(org.junit.function.ThrowingRunnable operation) {
        RuntimeException failure = assertThrows(RuntimeException.class, operation);
        return failure instanceof AppFailure
                ? FinanceErrors.failure(((AppFailure) failure).error)
                : (FinanceFailure) failure;
    }
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
        Transaction value = new Transaction(UUID.randomUUID().toString(), TransactionType.EXPENSE,
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
    @Test public void transferSendsBothWalletsWithoutCategoryOnPostAndPut() throws Exception {
        // A stale category on a transfer draft is never sent (backend answers 422 for a category).
        Transaction value = new Transaction("id", TransactionType.TRANSFER, 50_000, "stale", "from", "to", 0, "", "").withClientRequestId("3bef66f9-8969-447e-b0ea-aa94dd3422f3");
        FinanceApiBundle api = apiBundle(201, "{\"transactionId\":\"id\",\"transactionType\":\"TRANSFER\",\"status\":\"POSTED\"}");
        FinanceAction.transaction(value, false).remote.send(api);
        assertEquals("POST", captured.method());
        com.google.gson.JsonObject post = new Gson().fromJson(body(), com.google.gson.JsonObject.class);
        assertEquals("TRANSFER", post.get("transactionType").getAsString());
        assertEquals("from", post.get("walletId").getAsString());
        assertEquals("to", post.get("destinationWalletId").getAsString());
        assertEquals(50_000L, post.get("amountMinor").getAsLong());
        assertFalse(post.has("categoryId"));
        assertEquals(value.clientRequestId, post.get("clientRequestId").getAsString());
        FinanceAction.transaction(value, true).remote.send(api);
        assertEquals("PUT", captured.method()); assertEquals("/api/v1/transactions/id", captured.url().encodedPath());
        com.google.gson.JsonObject put = new Gson().fromJson(body(), com.google.gson.JsonObject.class);
        assertEquals("to", put.get("destinationWalletId").getAsString());
        assertFalse(put.has("categoryId")); assertFalse(put.has("clientRequestId"));
        // Income/expense never send a destination.
        Transaction expense = new Transaction("e", TransactionType.EXPENSE, 1, "c", "from", "to", 0, "", "");
        FinanceAction.transaction(expense, false).remote.send(api);
        assertFalse(new Gson().fromJson(body(), com.google.gson.JsonObject.class).has("destinationWalletId"));
    }
    @Test public void transferResponseMapsDestinationAndMockPostsIt() {
        TransactionResponse response = new Gson().fromJson("{\"transactionId\":\"t\",\"transactionType\":\"TRANSFER\",\"amountMinor\":1000,"
                + "\"categoryId\":null,\"walletId\":\"a\",\"destinationWalletId\":\"b\",\"occurredAt\":\"2026-11-07T08:45:00Z\","
                + "\"note\":\"\",\"payee\":\"\",\"status\":\"POSTED\"}", TransactionResponse.class);
        Transaction domain = response.domain();
        assertEquals(TransactionType.TRANSFER, domain.type); assertEquals("a", domain.walletId); assertEquals("b", domain.destinationWalletId);
        assertNull(domain.categoryId);
        FinanceBook book = new FinanceBook(FinanceBook.empty());
        String cash = book.addWallet("Tiền mặt", 100_000).id, bank = book.addWallet("Ngân hàng", 0).id;
        Transaction mock = new Transaction("t", TransactionType.TRANSFER, 1_000, null, cash, bank, 0, "", "");
        FinanceAction.transaction(mock, false).localResult.apply(book);
        assertEquals(99_000, book.balance(cash)); assertEquals(1_000, book.balance(bank));
    }
    @Test public void postUsesStableClientRequestIdSeparateFromIdAndPutOmitsIt() throws Exception {
        Transaction value = new Transaction("local-id", TransactionType.INCOME, 1_000, "c", "w", "ignored", 0, "", "").withClientRequestId("3bef66f9-8969-447e-b0ea-aa94dd3422f3");
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
        Transaction value = new Transaction("id", TransactionType.EXPENSE, 1_000, "c", "w", null, 0, "", "");
        FinanceFailure failure = financeFailure(() -> FinanceAction.transaction(value, false).remote
                .send(apiBundle(200, "{\"transactionId\":\"id\",\"status\":\"VOIDED\",\"voidedAt\":\"2026-11-07T08:45:00Z\"}")));
        assertEquals(FinanceProblem.VOIDED, failure.problem); assertEquals("TRANSACTION_REPLAY_VOIDED", failure.code);
    }
    @Test public void transactionErrorCodesAndFieldErrorsAreKeptForLocalisation() {
        FinanceFailure funds = financeFailure(() -> FinanceAction.deleteTransaction("id").remote.send(apiBundle(409,
                "{\"status\":409,\"errorCode\":\"WALLET_INSUFFICIENT_FUNDS\",\"message\":\"Wallet would be negative\"}")));
        assertEquals(FinanceProblem.INSUFFICIENT_FUNDS, funds.problem);
        FinanceFailure voided = financeFailure(() -> FinanceAction.transaction(new Transaction("id",
                TransactionType.EXPENSE, 1, "c", "w", null, 0, "", ""), true).remote.send(apiBundle(409, "{\"errorCode\":\"TRANSACTION_VOIDED\"}")));
        assertEquals(FinanceProblem.VOIDED, voided.problem);
        FinanceFailure fields = financeFailure(() -> FinanceAction.deleteTransaction("id").remote.send(apiBundle(422,
                "{\"errorCode\":\"VALIDATION_ERROR\",\"fieldErrors\":[{\"field\":\"amountMinor\",\"message\":\"must be positive\"}]}")));
        assertEquals(FinanceProblem.VALIDATION, fields.problem); assertEquals(java.util.Collections.singletonList("amountMinor"), fields.fields);
        FinanceFailure missing = financeFailure(() -> FinanceAction.deleteTransaction("id").remote.send(apiBundle(404,
                "{\"errorCode\":\"NOT_FOUND\",\"message\":\"Not found\"}")));
        assertEquals(FinanceProblem.NOT_FOUND, missing.problem);
    }
    @Test public void detailResponseCarriesLifecycleFields() {
        TransactionResponse response = new Gson().fromJson("{\"transactionId\":\"t\",\"transactionType\":\"EXPENSE\",\"amountMinor\":95000,"
                + "\"occurredAt\":\"2026-11-07T08:45:00Z\",\"status\":\"VOIDED\",\"voidedAt\":\"2026-11-08T00:00:00Z\","
                + "\"createdAt\":\"2026-11-07T08:45:01Z\",\"updatedAt\":\"2026-11-08T00:00:00Z\",\"clientRequestId\":\"k\"}", TransactionResponse.class);
        assertEquals("2026-11-07T08:45:01Z", response.createdAt); assertEquals("2026-11-08T00:00:00Z", response.updatedAt);
        Transaction value = response.domain();
        assertEquals("VOIDED", value.status); assertEquals("k", value.clientRequestId); assertEquals(95_000, value.amount);
    }
    @Test public void monthlyBudgetCreateAndDeleteUseTheMonthlyRoutes() throws Exception {
        java.util.List<Budget> lines = java.util.Collections.singletonList(new Budget("x", "food", "2026-11", 300_000));
        FinanceAction.monthlyBudget("m", "2026-11", lines).remote.send(apiBundle(201,
                "{\"monthlyBudgetId\":\"m\",\"month\":\"2026-11\",\"totalLimitMinor\":300000,\"spentMinor\":0,\"categories\":[]}"));
        assertEquals("POST", captured.method()); assertEquals("/api/v1/monthly-budgets", captured.url().encodedPath());
        com.google.gson.JsonObject json = new Gson().fromJson(body(), com.google.gson.JsonObject.class);
        assertEquals("2026-11", json.get("month").getAsString());
        assertFalse(json.has("totalLimitMinor"));
        assertEquals(300_000, json.getAsJsonArray("categories").get(0).getAsJsonObject().get("limitMinor").getAsLong());
        FinanceAction.deleteMonthlyBudget("m").remote.send(apiBundle(204, ""));
        assertEquals("DELETE", captured.method()); assertEquals("/api/v1/monthly-budgets/m", captured.url().encodedPath());
    }
    @Test public void shortfallAndBudgetCodesAreKeptOnTheFailure() {
        FinanceFailure shortfall = financeFailure(() -> FinanceAction.budget(
                new Budget("b", "food", "2026-11", 1), false).remote.send(apiBundle(409,
                "{\"status\":409,\"errorCode\":\"BUDGET_EXCEEDS_AVAILABLE_BALANCE\",\"message\":\"x\",\"shortfallMinor\":30000}")));
        assertEquals(FinanceProblem.CONFLICT, shortfall.problem);
        assertEquals(FinanceBook.BUDGET_EXCEEDS_AVAILABLE_BALANCE, shortfall.code);
        assertEquals(Long.valueOf(30_000), shortfall.shortfallMinor);
        FinanceFailure current = financeFailure(() -> FinanceAction.deleteMonthlyBudget("m").remote.send(
                apiBundle(409, "{\"status\":409,\"errorCode\":\"BUDGET_MONTH_CURRENT\",\"message\":\"x\"}")));
        assertEquals(FinanceBook.BUDGET_MONTH_CURRENT, current.code);
        assertNull(current.shortfallMinor);
    }
    @Test public void budgetRequestUsesMonthAndLimitAndSupports204Delete() throws Exception {
        FinanceAction.budget(new Budget("b", "food", "2026-10", 1_500_000), true).remote.send(apiBundle(200, "{\"budgetId\":\"b\"}"));
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
        FinanceBook.Query query = FinanceBook.Query.month("2026-02", TransactionType.INCOME, "w", "c", "  50% off ");
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
        FinanceProblem[] problems = {FinanceProblem.UNAUTHORIZED, FinanceProblem.NOT_FOUND,
                FinanceProblem.CONFLICT, FinanceProblem.VALIDATION, FinanceProblem.SERVER};
        for (int i = 0; i < statuses.length; i++) {
            FinanceApiBundle bundle = apiBundle(statuses[i], "{\"message\":\"fixture\"}");
            assertEquals(problems[i], financeFailure(() -> FinanceAction.required(bundle.wallets.wallets())).problem);
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
        assertEquals(FinanceProblem.NETWORK, financeFailure(() -> FinanceAction.required(api.wallets.wallets())).problem);
    }
    @Test public void timestampsAcceptServerPrecisionAndRejectInvalidDates() {
        assertEquals(TransactionResponse.parseTimestamp("2026-10-31T17:00:00Z"), TransactionResponse.parseTimestamp("2026-10-31T17:00:00.000000Z"));
        assertEquals("2026-11", FinanceBook.month(TransactionResponse.parseTimestamp("2026-10-31T17:00:00Z")));
        financeFailure(() -> TransactionResponse.parseTimestamp("2026-02-30T17:00:00Z"));
        financeFailure(() -> TransactionResponse.parseTimestamp("2026-10-31"));
    }
    @Test public void helperRequestsFollowWalletAndCategoryVocabulary() throws Exception {
        FinanceAction.wallet("Tiền mặt", 20_000, WalletType.CASH).remote.send(apiBundle(201, "{\"walletId\":\"w\"}"));
        assertEquals("/api/v1/wallets", captured.url().encodedPath());
        assertEquals("CASH", new Gson().fromJson(body(), com.google.gson.JsonObject.class).get("walletType").getAsString());
        FinanceAction.category("Ăn uống", TransactionType.EXPENSE).remote.send(apiBundle(201, "{\"categoryId\":\"c\"}"));
        assertEquals("/api/v1/categories", captured.url().encodedPath());
    }
}
