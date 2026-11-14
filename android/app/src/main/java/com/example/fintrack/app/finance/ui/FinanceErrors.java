package com.example.fintrack.app.finance.ui;

import com.example.fintrack.app.finance.domain.FinanceFailure;
import com.example.fintrack.app.finance.domain.FinanceProblem;

import com.example.fintrack.app.core.error.AppError;
import java.util.ArrayList;

/** Reads a finance problem back out of an AppError; FinanceRepository.error builds the AppError. */
public final class FinanceErrors {
    private FinanceErrors() { }
    public static FinanceProblem problem(AppError error) {
        if (error.category == AppError.Category.DOMAIN && error.cause instanceof FinanceFailure)
            return ((FinanceFailure) error.cause).problem;
        if (error.code != null) switch (error.code) {
            case "WALLET_INSUFFICIENT_FUNDS": return FinanceProblem.INSUFFICIENT_FUNDS;
            case "TRANSACTION_VOIDED": case "TRANSACTION_REPLAY_VOIDED": return FinanceProblem.VOIDED;
            case "WALLET_ARCHIVED": case "CATEGORY_ARCHIVED": return FinanceProblem.ARCHIVED;
        }
        if (error.category == AppError.Category.NETWORK) return FinanceProblem.NETWORK;
        if (error.category == AppError.Category.STORAGE) return FinanceProblem.STORAGE;
        int status = error.httpStatus == null ? 0 : error.httpStatus;
        return status == 401 ? FinanceProblem.UNAUTHORIZED : status == 404 ? FinanceProblem.NOT_FOUND
                : status == 409 ? FinanceProblem.CONFLICT : status == 400 || status == 422
                ? FinanceProblem.VALIDATION : FinanceProblem.SERVER;
    }
    public static FinanceFailure failure(AppError error) {
        java.util.List<String> fields = new ArrayList<>();
        for (AppError.FieldError field : error.fieldErrors) fields.add(field.field);
        return new FinanceFailure(problem(error),error.code,fields,error.shortfallMinor);
    }
}
