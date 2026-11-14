package com.example.fintrack.app.core.network;

import static org.junit.Assert.*;
import com.example.fintrack.app.core.error.AppError;
import com.example.fintrack.app.testing.StubCall;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Response;

public class TypedResponseTest {
    private AppError parse(int status, String json) {
        return HttpErrorParser.parse(Response.error(status, ResponseBody.create(MediaType.get("application/json"), json)));
    }
    @Test public void preservesStatusCodeAndFieldsWithoutDisplayingServerText() {
        AppError error = parse(422, "{\"errorCode\":\"NEW_CODE\",\"message\":\"internal detail\",\"fieldErrors\":[{\"field\":\"amountMinor\",\"message\":\"invalid\"}],\"shortfallMinor\":123}");
        assertEquals(Integer.valueOf(422), error.httpStatus);
        assertEquals("NEW_CODE", error.code);
        assertEquals("amountMinor", error.fieldErrors.get(0).field);
        assertEquals(Long.valueOf(123), error.shortfallMinor);
        assertThrows(UnsupportedOperationException.class, () -> error.fieldErrors.clear());
    }
    @Test public void malformedAndEmptyBodiesRetainHttpStatus() {
        for (String body : new String[]{"", "broken", "null", "[]"}) {
            AppError error = parse(503, body);
            assertEquals(Integer.valueOf(503), error.httpStatus);
            assertEquals(AppError.Category.HTTP, error.category);
        }
    }
    @Test public void invalidShortfallCannotDiscardBackendCode() {
        for (String value : new String[]{"\"123\"", "1.1", "9223372036854775808", "null", "true", "{}"}) {
            AppError error = parse(409, "{\"errorCode\":\"BUDGET_EXCEEDS_AVAILABLE_BALANCE\",\"shortfallMinor\":"+value+"}");
            assertEquals("BUDGET_EXCEEDS_AVAILABLE_BALANCE", error.code); assertNull(error.shortfallMinor);
        }
        assertEquals(Long.valueOf(Long.MAX_VALUE), parse(409,"{\"shortfallMinor\":9223372036854775807}").shortfallMinor);
    }
    @Test public void cancelSuppressesQueuedDeliveryAndIsIdempotent() {
        class Call extends StubCallBase { int cancels; @Override public void cancel() { cancels++; } }
        Call call = new Call();
        List<Runnable> queue = new ArrayList<>();
        List<Resource<String>> results = new ArrayList<>();
        RequestHandle handle = ApiCalls.enqueue(call, true, queue::add, results::add);
        call.succeed(new ApiResponse<>(true, null, "ok", 200));
        handle.cancel(); handle.cancel();
        queue.forEach(Runnable::run);
        assertEquals(1, call.cancels); assertTrue(results.isEmpty());
    }
    @Test public void cancellationBeforeResponseSuppressesDelivery() {
        StubCall<ApiResponse<String>> call = new StubCall<>();
        List<Resource<String>> results = new ArrayList<>();
        RequestHandle handle = ApiCalls.enqueue(call, true, Runnable::run, results::add);
        handle.cancel(); call.succeed(new ApiResponse<>(true, null, "ok", 200));
        assertTrue(results.isEmpty());
    }
    @Test public void optionalVoidAcceptsEmptyButRejectsFailedEnvelope() {
        StubCall<ApiResponse<Void>> empty = new StubCall<>();
        List<Resource<Void>> results = new ArrayList<>();
        ApiCalls.enqueue(empty, false, Runnable::run, results::add); empty.succeed(null);
        assertEquals(Resource.Status.SUCCESS, results.get(0).getStatus());
        StubCall<ApiResponse<Void>> failed = new StubCall<>();
        ApiCalls.enqueue(failed, false, Runnable::run, results::add);
        failed.succeed(new ApiResponse<>(false,"detail",null,400));
        assertEquals("LOCAL_API_FAILURE", results.get(1).getError().code);
    }
    @Test public void requiredDataAndAtMostOnceDelivery() {
        StubCall<ApiResponse<String>> call = new StubCall<>();
        List<Resource<String>> results = new ArrayList<>();
        ApiCalls.enqueue(call, true, Runnable::run, results::add);
        call.succeed(new ApiResponse<>(true, null, null, 200));
        call.fail(new java.io.IOException());
        assertEquals(1, results.size()); assertEquals("LOCAL_MISSING_DATA", results.get(0).getError().code);
    }
    private static class StubCallBase implements retrofit2.Call<ApiResponse<String>> {
        retrofit2.Callback<ApiResponse<String>> callback;
        void succeed(ApiResponse<String> value) { callback.onResponse(this, Response.success(value)); }
        public void enqueue(retrofit2.Callback<ApiResponse<String>> value) { callback=value; }
        public Response<ApiResponse<String>> execute() { throw new UnsupportedOperationException(); }
        public void cancel() { }
        public boolean isCanceled() { return false; }
        public boolean isExecuted() { return false; }
        public retrofit2.Call<ApiResponse<String>> clone() { return new StubCallBase(); }
        public okhttp3.Request request() { return new okhttp3.Request.Builder().url("https://test.invalid/").build(); }
        public okio.Timeout timeout() { return okio.Timeout.NONE; }
    }
}
