package com.example.fintrack.app.core.error;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Transport/domain details independent of presentation and Android. */
public final class AppError {
    public enum Category { NETWORK, HTTP, DOMAIN, STORAGE, UNEXPECTED }
    public final Category category;
    public final Integer httpStatus;
    public final String code;
    public final List<FieldError> fieldErrors;
    public final Long shortfallMinor;
    public final Throwable cause;
    public AppError(Category category, Integer httpStatus, String code, List<FieldError> fields,
                    Long shortfallMinor, Throwable cause) {
        this.category = Objects.requireNonNull(category);
        this.httpStatus = httpStatus; this.code = code;
        this.fieldErrors = fields == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(fields));
        this.shortfallMinor = shortfallMinor; this.cause = cause;
    }
    public static AppError local(Category category, String code, Throwable cause) {
        return new AppError(category, null, code, null, null, cause);
    }
    /** Code of {@link #sessionExpired()}. */
    public static final String SESSION_EXPIRED = "SESSION_EXPIRED";
    /** A ViewModel's work outlived the session that started it. A 401, so FinanceErrors maps it to UNAUTHORIZED. */
    public static AppError sessionExpired() {
        return new AppError(Category.HTTP, 401, SESSION_EXPIRED, null, null, null);
    }
    public static final class FieldError {
        public final String field, message;
        public FieldError(String field, String message) { this.field = field; this.message = message; }
    }
}
