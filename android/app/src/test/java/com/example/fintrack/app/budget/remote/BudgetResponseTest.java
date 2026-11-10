package com.example.fintrack.app.budget.remote;

import static org.junit.Assert.*;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.google.gson.Gson;
import org.junit.Test;

public class BudgetResponseTest {
    @Test public void missingUsageFailsInsteadOfComputingFromPartialHistory() {
        BudgetResponse response = new Gson().fromJson("{\"budgetId\":\"b\",\"categoryId\":\"food\",\"month\":\"2026-11\",\"limitMinor\":100000}", BudgetResponse.class);
        assertThrows(FinanceBook.Failure.class, response::domain);
    }
    @Test public void authoritativeUsageDoesNotDependOnTransactionPages() {
        BudgetResponse response = new Gson().fromJson("{\"budgetId\":\"b\",\"categoryId\":\"food\",\"month\":\"2026-11\",\"limitMinor\":100000,\"spentMinor\":85000,\"warningThresholdPercent\":90,\"status\":\"SAFE\"}", BudgetResponse.class);
        FinanceBook.State state = FinanceBook.empty(); state.budgets.add(response.domain());
        FinanceBook book = new FinanceBook(state);
        FinanceBook.BudgetUsage usage = book.usage(book.budget("b"));
        assertEquals(85000, usage.spent);
        assertEquals(15000, usage.remaining);
        assertFalse(usage.warning);
        assertFalse(usage.exceeded);
    }
    @Test public void mapsArchivedBudgetAndZeroSpentWithoutNullFallback() {
        BudgetResponse response = new Gson().fromJson("{\"budgetId\":\"b\",\"categoryId\":\"food\",\"month\":\"2026-11\",\"limitMinor\":100000,\"spentMinor\":0,\"archivedAt\":\"2026-11-07T00:00:00Z\"}", BudgetResponse.class);
        assertEquals(Long.valueOf(0), response.domain().serverSpentMinor);
        assertEquals(80, response.domain().warningThresholdPercent);
        assertNotNull(response.domain().archivedAt);
        BudgetRequest request = new BudgetRequest(response.domain());
        String json = new Gson().toJson(request);
        assertFalse(json.contains("spentMinor"));
        assertFalse(json.contains("archivedAt"));
    }
}
