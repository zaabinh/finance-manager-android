package com.fintrack.api.exception;

public enum ErrorCode {
    VALIDATION_ERROR,
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    METHOD_NOT_ALLOWED,
    UNSUPPORTED_MEDIA_TYPE,
    REQUEST_ERROR,
    INTERNAL_ERROR;

    public static ErrorCode forStatus(int status) {
        return switch (status) {
            case 400 -> BAD_REQUEST;
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default -> status >= 500 ? INTERNAL_ERROR : REQUEST_ERROR;
        };
    }
}
