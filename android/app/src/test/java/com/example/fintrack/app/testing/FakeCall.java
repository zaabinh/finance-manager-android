package com.example.fintrack.app.testing;

import okhttp3.Request;
import okio.Timeout;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** A Retrofit call that answers immediately, on the calling thread, with a fixed HTTP 200 body. */
public final class FakeCall<T> implements Call<T> {
    private final T body;

    public FakeCall(T body) {
        this.body = body;
    }

    @Override public Response<T> execute() { return Response.success(body); }
    @Override public void enqueue(Callback<T> callback) { callback.onResponse(this, execute()); }
    @Override public boolean isExecuted() { return false; }
    @Override public void cancel() { }
    @Override public boolean isCanceled() { return false; }
    @Override public Call<T> clone() { return this; }
    @Override public Request request() { return new Request.Builder().url("http://localhost/").build(); }
    @Override public Timeout timeout() { return Timeout.NONE; }
}
