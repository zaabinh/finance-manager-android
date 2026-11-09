package com.fintrack.api.exception;

import org.springframework.http.HttpStatus;

/** 409 BUDGET_EXCEEDS_AVAILABLE_BALANCE; the body carries how much spendable money is missing. */
public class BudgetShortfallException extends AppException {
    private final long shortfallMinor;

    public BudgetShortfallException(long shortfallMinor) {
        super(HttpStatus.CONFLICT, ErrorCode.BUDGET_EXCEEDS_AVAILABLE_BALANCE,
                "Ngân sách vượt quá số dư khả dụng");
        this.shortfallMinor = shortfallMinor;
    }

    public long getShortfallMinor() { return shortfallMinor; }
}
