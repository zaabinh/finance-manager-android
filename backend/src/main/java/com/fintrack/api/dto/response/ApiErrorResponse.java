package com.fintrack.api.dto.response;

import com.fintrack.api.exception.ErrorCode;
import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        ErrorCode errorCode,
        String message,
        String path,
        List<FieldError> fieldErrors) {

    public static ApiErrorResponse of(int status, ErrorCode errorCode, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status, errorCode, message, path, List.of());
    }

    public record FieldError(String field, String message) {
    }
}
