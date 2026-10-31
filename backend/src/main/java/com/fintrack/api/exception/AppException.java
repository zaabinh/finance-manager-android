package com.fintrack.api.exception;

import org.springframework.http.HttpStatus;

public class AppException extends RuntimeException {
    private final HttpStatus status;
    private final ErrorCode errorCode;

    public AppException(HttpStatus status, ErrorCode errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() { return status; }
    public ErrorCode getErrorCode() { return errorCode; }
}
