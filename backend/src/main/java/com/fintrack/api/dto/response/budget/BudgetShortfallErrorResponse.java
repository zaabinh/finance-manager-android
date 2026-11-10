package com.fintrack.api.dto.response.budget;

import com.fintrack.api.dto.response.ApiErrorResponse;
import com.fintrack.api.exception.ErrorCode;
import java.time.Instant;
import java.util.List;

/** The standard error envelope plus {@code shortfallMinor} for BUDGET_EXCEEDS_AVAILABLE_BALANCE. */
public record BudgetShortfallErrorResponse(Instant timestamp, int status, ErrorCode errorCode, String message,
        String path, List<ApiErrorResponse.FieldError> fieldErrors, long shortfallMinor) {
    public static BudgetShortfallErrorResponse of(ErrorCode code, String message, String path, long shortfallMinor) {
        return new BudgetShortfallErrorResponse(Instant.now(), 409, code, message, path, List.of(), shortfallMinor);
    }
}
