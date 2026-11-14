package com.example.fintrack.app.core.network;

import com.example.fintrack.app.core.error.AppError;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Shared terminal response delivery; ViewModels own observable loading state. */
public final class ApiCalls {
    private ApiCalls() { }
    public static <T> RequestHandle enqueue(Call<ApiResponse<T>> call, boolean required, Executor delivery,
                                            Consumer<Resource<T>> callback) {
        Completion<T> completion = new Completion<>(call, delivery, callback);
        call.enqueue(new Callback<ApiResponse<T>>() {
            @Override public void onResponse(Call<ApiResponse<T>> ignored, Response<ApiResponse<T>> response) {
                if (!response.isSuccessful()) {
                    completion.deliver(Resource.error(HttpErrorParser.parse(response), null)); return;
                }
                ApiResponse<T> body = response.body();
                if ((body != null && !body.isSuccess()) || (required && (body == null || body.getData() == null))) {
                    completion.deliver(Resource.error(new AppError(AppError.Category.HTTP, response.code(),
                            body != null && !body.isSuccess() ? "LOCAL_API_FAILURE" : "LOCAL_MISSING_DATA",
                            null, null, null), null));
                } else completion.deliver(Resource.success(body == null ? null : body.getData()));
            }
            @Override public void onFailure(Call<ApiResponse<T>> ignored, Throwable error) {
                completion.deliver(Resource.error(HttpErrorParser.from(error), null));
            }
        });
        return completion;
    }
    private static final class Completion<T> implements RequestHandle {
        private final Call<?> call;
        private final Executor delivery;
        private final Consumer<Resource<T>> callback;
        private boolean canceled, queued, delivered;
        Completion(Call<?> call, Executor delivery, Consumer<Resource<T>> callback) {
            this.call = call; this.delivery = delivery; this.callback = callback;
        }
        void deliver(Resource<T> result) {
            synchronized (this) { if (canceled || queued) return; queued = true; }
            delivery.execute(() -> {
                synchronized (this) {
                    if (canceled || delivered) return;
                    delivered = true;
                    callback.accept(result);
                }
            });
        }
        @Override public void cancel() {
            synchronized (this) { if (canceled) return; canceled = true; }
            call.cancel();
        }
    }
}
