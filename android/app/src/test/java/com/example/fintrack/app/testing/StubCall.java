package com.example.fintrack.app.testing;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okio.Timeout;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * A Retrofit call the test controls. enqueue() only keeps the callback: the test answers later with
 * succeed()/reject()/fail(), in any order. execute() runs the script given to the constructor.
 */
public final class StubCall<T> implements Call<T> {
    public interface Execution<T> { Response<T> run() throws IOException; }

    private final Execution<T> execution;
    private Callback<T> callback;

    public StubCall() { this(null); }
    public StubCall(Execution<T> execution) { this.execution = execution; }

    /** An HTTP error response with an empty body. */
    public static <T> Response<T> http(int code) {
        return Response.error(code, ResponseBody.create((MediaType) null, ""));
    }

    public void succeed(T body) { callback.onResponse(this, Response.success(body)); }
    public void reject(int code) { callback.onResponse(this, http(code)); }
    public void fail(Throwable error) { callback.onFailure(this, error); }

    @Override public Response<T> execute() throws IOException { return execution.run(); }
    @Override public void enqueue(Callback<T> callback) { this.callback = callback; }
    @Override public boolean isExecuted() { return false; }
    @Override public void cancel() { }
    @Override public boolean isCanceled() { return false; }
    @Override public Call<T> clone() { return this; }
    @Override public Request request() { return new Request.Builder().url("http://localhost/").build(); }
    @Override public Timeout timeout() { return Timeout.NONE; }
}
