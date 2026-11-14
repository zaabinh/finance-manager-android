package com.example.fintrack.app.core.error;

/** Ngoại lệ dùng chung trong toàn bộ nghiệp vụ ứng dụng */
public final class AppFailure extends RuntimeException {
    public final AppError error;

    public AppFailure(AppError error) {
        super(error.code, error.cause);
        this.error = error;
    }
}
