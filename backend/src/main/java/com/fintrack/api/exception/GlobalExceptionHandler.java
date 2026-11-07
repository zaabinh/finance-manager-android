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
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (isFinance(request)) status = HttpStatus.UNPROCESSABLE_ENTITY;
        var fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        var response = new ApiErrorResponse(Instant.now(), status.value(), ErrorCode.VALIDATION_ERROR,
                "Request validation failed", path(request), fieldErrors);
        return new ResponseEntity<>(response, headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (isFinance(request)) {
            Throwable cause = exception;
            while (cause != null) {
                if (cause instanceof AppException app) {
                    return ResponseEntity.status(app.getStatus()).body(ApiErrorResponse.of(
                            app.getStatus().value(), app.getErrorCode(), app.getMessage(), path(request)));
                }
                cause = cause.getCause();
            }
        }
        return handleExceptionInternal(exception, null, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> handleIntegrity(DataIntegrityViolationException exception, WebRequest request) {
        if (isFinance(request)) {
            String detail = String.valueOf(exception.getMostSpecificCause().getMessage());
            ErrorCode code = detail.contains("ux_wallets_user_name_active") ? ErrorCode.WALLET_NAME_CONFLICT
                    : detail.contains("ux_categories_user_type_name_active") ? ErrorCode.CATEGORY_NAME_CONFLICT
                    : detail.contains("budgets_no_overlap") ? ErrorCode.BUDGET_PERIOD_CONFLICT
                    : detail.contains("ux_transactions_idempotency") ? ErrorCode.TRANSACTION_REQUEST_CONFLICT : null;
            if (code != null) return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of(
                    409, code, "Dữ liệu bị trùng với bản ghi đang hoạt động", path(request)));
        }
        return handleUnexpected(exception, request);
    }

    /**
     * Races only: serialization (40001), deadlock (40P01), unmapped unique (23505) or exclusion (23P01), incl. at commit.
     * Ledger trigger (P0001) and NOT NULL/CHECK/FK violations are coding bugs and stay 500.
     */
    private static boolean isConcurrencyRace(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sql && sql.getSQLState() != null) {
                if (java.util.Set.of("40001", "40P01", "23505", "23P01").contains(sql.getSQLState())) return true;
            }
        }
        return false;
    }

    private boolean isFinance(WebRequest request) {
        return path(request).matches("/api/v1/(wallets|categories|budgets|transactions)(/.*)?");
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

    @ExceptionHandler(AppException.class)
    ResponseEntity<ApiErrorResponse> handleAppException(AppException exception, WebRequest request) {
        return ResponseEntity.status(exception.getStatus()).body(ApiErrorResponse.of(
                exception.getStatus().value(), exception.getErrorCode(), exception.getMessage(), path(request)));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, WebRequest request) {
        if (isFinance(request) && isConcurrencyRace(exception)) {
            LOGGER.warn("Finance request lost a concurrency race: {}", path(request), exception);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of(409, ErrorCode.forStatus(409),
                    "Dữ liệu đã thay đổi hoặc vi phạm ràng buộc, vui lòng tải lại", path(request)));
        }
        LOGGER.error("Unexpected request processing failure", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiErrorResponse.of(
                500, ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", path(request)));
    }

    private String path(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }
}
