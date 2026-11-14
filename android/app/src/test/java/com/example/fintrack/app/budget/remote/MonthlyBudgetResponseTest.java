package com.example.fintrack.app.budget.remote;

import com.example.fintrack.app.finance.domain.Budget;
import com.example.fintrack.app.finance.domain.BudgetUsage;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.FinanceFailure;
import com.example.fintrack.app.finance.domain.FinanceSnapshot;
import com.example.fintrack.app.finance.domain.MonthlyBudget;

import static org.junit.Assert.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Arrays;
import org.junit.Test;

public class MonthlyBudgetResponseTest {
    private static final String CHILD = "{\"budgetId\":\"b\",\"monthlyBudgetId\":\"m\",\"categoryId\":\"food\",\"month\":\"2026-11\","
            + "\"limitMinor\":300000,\"spentMinor\":120000,\"warningThresholdPercent\":80}";

    @Test public void mapsServerAggregatesAndChildrenIntoTheSnapshot() {
        MonthlyBudgetResponse response = new Gson().fromJson("{\"monthlyBudgetId\":\"m\",\"month\":\"2026-11\",\"totalLimitMinor\":300000,"
                + "\"spentMinor\":450000,\"remainingMinor\":-150000,\"usagePercent\":150.00,\"warningThresholdPercent\":80,"
                + "\"status\":\"EXCEEDED\",\"categories\":[" + CHILD + "]}", MonthlyBudgetResponse.class);
        FinanceSnapshot.Builder state = FinanceBook.empty();
        response.into(state);
        FinanceBook book = new FinanceBook(state);
        MonthlyBudget monthly = book.monthlyBudgetFor("2026-11");
        // Monthly spent is all expenses of the month (server), not the sum of category spent.
        assertEquals(450_000, book.monthlySpent(monthly));
        assertEquals(300_000, book.monthlyTotal(monthly));
        assertTrue(book.monthlyUsage(monthly).exceeded);
        assertEquals(-150_000, book.monthlyUsage(monthly).remaining);
        Budget child = book.categoryBudgets(monthly).get(0);
        assertEquals("m", child.monthlyBudgetId);
        assertEquals(120_000, book.spent(child));
    }

    @Test public void missingAggregatesOrForeignChildFailInsteadOfRecomputing() {
        MonthlyBudgetResponse noSpent = new Gson().fromJson("{\"monthlyBudgetId\":\"m\",\"month\":\"2026-11\",\"totalLimitMinor\":0,\"categories\":[]}", MonthlyBudgetResponse.class);
        assertThrows(FinanceFailure.class, noSpent::domain);
        MonthlyBudgetResponse noCategories = new Gson().fromJson("{\"monthlyBudgetId\":\"m\",\"month\":\"2026-11\",\"totalLimitMinor\":0,\"spentMinor\":0}", MonthlyBudgetResponse.class);
        assertThrows(FinanceFailure.class, noCategories::domain);
        MonthlyBudgetResponse foreign = new Gson().fromJson("{\"monthlyBudgetId\":\"other\",\"month\":\"2026-11\",\"totalLimitMinor\":300000,"
                + "\"spentMinor\":0,\"categories\":[" + CHILD + "]}", MonthlyBudgetResponse.class);
        assertThrows(FinanceFailure.class, () -> foreign.into(FinanceBook.empty()));
    }

    @Test public void emptyMonthlyBudgetHasZeroTotalAndIsSafe() {
        MonthlyBudgetResponse response = new Gson().fromJson("{\"monthlyBudgetId\":\"m\",\"month\":\"2026-11\",\"totalLimitMinor\":0,"
                + "\"spentMinor\":0,\"categories\":[]}", MonthlyBudgetResponse.class);
        FinanceSnapshot.Builder state = FinanceBook.empty(); response.into(state);
        FinanceBook book = new FinanceBook(state);
        BudgetUsage usage = book.monthlyUsage(book.monthlyBudgetFor("2026-11"));
        assertFalse(usage.warning); assertFalse(usage.exceeded); assertEquals("0", usage.percentage);
    }

    @Test public void createRequestSendsMonthAndCategoryLinesWithoutAnyTotal() {
        MonthlyBudgetRequest request = new MonthlyBudgetRequest("2026-11", Arrays.asList(
                new Budget("x", "food", "2026-11", 300_000), new Budget("y", "bills", "2026-11", 200_000)));
        JsonObject json = new Gson().toJsonTree(request).getAsJsonObject();
        assertEquals("2026-11", json.get("month").getAsString());
        assertEquals(2, json.getAsJsonArray("categories").size());
        JsonObject first = json.getAsJsonArray("categories").get(0).getAsJsonObject();
        assertEquals("food", first.get("categoryId").getAsString());
        assertEquals(300_000, first.get("limitMinor").getAsLong());
        assertEquals(2, first.size());
        assertEquals(2, json.size());
    }
}
