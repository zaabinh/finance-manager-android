package com.example.fintrack.app.finance.ui;

import static org.junit.Assert.*;
import com.example.fintrack.app.core.error.AppError;
import com.example.fintrack.app.finance.domain.*;
import java.util.Collections;
import org.junit.Test;

public class FinanceErrorsTest {
    @Test public void customDomainCodeRetainsProblemFieldsAndZeroShortfall() {
        FinanceFailure failure=new FinanceFailure(FinanceProblem.CONFLICT,
                FinanceBook.BUDGET_EXCEEDS_AVAILABLE_BALANCE,Collections.singletonList("limitMinor"),0L);
        AppError error=FinanceErrors.from(failure);
        FinanceFailure restored=FinanceErrors.failure(error);
        assertEquals(FinanceProblem.CONFLICT,restored.problem);
        assertEquals(failure.code,restored.code);
        assertEquals(failure.fields,restored.fields);
        assertEquals(Long.valueOf(0),restored.shortfallMinor);
    }
    @Test public void unknownBackendCodeKeepsHttpMeaningAndMetadata() {
        AppError error=new AppError(AppError.Category.HTTP,409,"FUTURE_CODE",Collections.emptyList(),12L,null);
        FinanceFailure restored=FinanceErrors.failure(error);
        assertEquals(FinanceProblem.CONFLICT,restored.problem);
        assertEquals("FUTURE_CODE",restored.code);assertEquals(Long.valueOf(12),restored.shortfallMinor);
    }
}
