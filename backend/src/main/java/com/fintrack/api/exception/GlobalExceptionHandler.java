package com.fintrack.api.exception;

import com.fintrack.api.dto.response.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiErrorResponse.FieldError(error.getField(), "Invalid value"))
                .toList();
        var response = new ApiErrorResponse(Instant.now(), status.value(), ErrorCode.VALIDATION_ERROR,
                "Request validation failed", path(request), fieldErrors);
        return new ResponseEntity<>(response, headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            LOGGER.error("Unexpected request processing failure", exception);
        }
        String message = status.is5xxServerError() ? "An unexpected error occurred"
                : "Request could not be processed";
        var response = ApiErrorResponse.of(status.value(), ErrorCode.forStatus(status.value()), message, path(request));
        return new ResponseEntity<>(response, headers, status);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception, WebRequest request) {
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(400, ErrorCode.VALIDATION_ERROR,
                "Request validation failed", path(request)));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, WebRequest request) {
        LOGGER.error("Unexpected request processing failure", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiErrorResponse.of(
                500, ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", path(request)));
    }

    private String path(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }
}
