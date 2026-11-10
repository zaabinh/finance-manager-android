package com.fintrack.api.dto.response;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        int code
) {
}
