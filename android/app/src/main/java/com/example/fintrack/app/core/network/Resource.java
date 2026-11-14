package com.example.fintrack.app.core.network;

import com.example.fintrack.app.core.error.AppError;

/** Observable state. Only ERROR carries a typed error; retained data may accompany any state. */
public final class Resource<T> {
    public enum Status { SUCCESS, ERROR, LOADING }
    private final Status status;
    private final T data;
    private final AppError error;
    private Resource(Status status, T data, AppError error) {
        this.status = status; this.data = data; this.error = error;
    }
    public static <T> Resource<T> success(T data) { return new Resource<>(Status.SUCCESS, data, null); }
    public static <T> Resource<T> loading(T data) { return new Resource<>(Status.LOADING, data, null); }
    public static <T> Resource<T> error(AppError error, T data) {
        return new Resource<>(Status.ERROR, data, java.util.Objects.requireNonNull(error));
    }
    public Status getStatus() { return status; }
    public T getData() { return data; }
    public AppError getError() { return error; }
}
