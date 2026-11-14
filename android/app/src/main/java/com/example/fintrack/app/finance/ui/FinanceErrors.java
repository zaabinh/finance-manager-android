package com.example.fintrack.app.finance.ui;

import com.example.fintrack.app.finance.domain.Category;
import com.example.fintrack.app.finance.domain.FinanceFailure;
import com.example.fintrack.app.finance.domain.FinanceProblem;

import com.example.fintrack.app.core.error.AppError;
import java.util.ArrayList;

/** Explicit translation between finance failures and presentation errors. */
public final class FinanceErrors {
    private FinanceErrors() { }
    public static AppError from(FinanceFailure failure) {
        String local;
        switch (failure.problem) {
            case AMOUNT: local = "FINANCE_AMOUNT"; break;
            case CATEGORY: local = "FINANCE_CATEGORY"; break;
            case WALLET: local = "FINANCE_WALLET"; break;
            case SAME_WALLET: local = "FINANCE_SAME_WALLET"; break;
            case DUPLICATE_BUDGET: local = "FINANCE_DUPLICATE_BUDGET"; break;
            case NOT_FOUND: local = "FINANCE_NOT_FOUND"; break;
            case NAME: local = "FINANCE_NAME"; break;
            case DATE: local = "FINANCE_DATE"; break;
            case STORAGE: local = "FINANCE_STORAGE"; break;
            case NETWORK: local = "FINANCE_NETWORK"; break;
            case UNAUTHORIZED: local = "FINANCE_UNAUTHORIZED"; break;
            case CONFLICT: local = "FINANCE_CONFLICT"; break;
            case VALIDATION: local = "FINANCE_VALIDATION"; break;
            case SERVER: local = "FINANCE_SERVER"; break;
            case INSUFFICIENT_FUNDS: local = "FINANCE_INSUFFICIENT_FUNDS"; break;
            case VOIDED: local = "FINANCE_VOIDED"; break;
            case ARCHIVED: local = "FINANCE_ARCHIVED"; break;
            default: local = "FINANCE_SERVER";
        }
        java.util.List<AppError.FieldError> fields = new ArrayList<>();
        for (String field : failure.fields) fields.add(new AppError.FieldError(field, null));
        return new AppError(failure.problem == FinanceProblem.STORAGE ? AppError.Category.STORAGE : AppError.Category.DOMAIN,
                null, failure.code == null ? local : failure.code, fields, failure.shortfallMinor, failure);
    }
    public static FinanceProblem problem(AppError error) {
        if (error.category == AppError.Category.DOMAIN && error.cause instanceof FinanceFailure)
            return ((FinanceFailure) error.cause).problem;
        if (error.code != null) switch (error.code) {
            case "FINANCE_AMOUNT": return FinanceProblem.AMOUNT;
            case "FINANCE_CATEGORY": return FinanceProblem.CATEGORY;
            case "FINANCE_WALLET": return FinanceProblem.WALLET;
            case "FINANCE_SAME_WALLET": return FinanceProblem.SAME_WALLET;
            case "FINANCE_DUPLICATE_BUDGET": return FinanceProblem.DUPLICATE_BUDGET;
            case "FINANCE_NOT_FOUND": return FinanceProblem.NOT_FOUND;
            case "FINANCE_NAME": return FinanceProblem.NAME;
            case "FINANCE_DATE": return FinanceProblem.DATE;
            case "FINANCE_STORAGE": return FinanceProblem.STORAGE;
            case "FINANCE_NETWORK": return FinanceProblem.NETWORK;
            case "FINANCE_UNAUTHORIZED": return FinanceProblem.UNAUTHORIZED;
            case "FINANCE_CONFLICT": return FinanceProblem.CONFLICT;
            case "FINANCE_VALIDATION": return FinanceProblem.VALIDATION;
            case "FINANCE_SERVER": return FinanceProblem.SERVER;
            case "FINANCE_INSUFFICIENT_FUNDS": return FinanceProblem.INSUFFICIENT_FUNDS;
            case "FINANCE_VOIDED": return FinanceProblem.VOIDED;
            case "FINANCE_ARCHIVED": return FinanceProblem.ARCHIVED;
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
