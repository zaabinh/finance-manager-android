package com.fintrack.api.dto.request.budget;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fintrack.api.service.finance.FinanceLongDeserializer;
import com.fintrack.api.service.finance.FinanceRequest;
import java.util.List;
import java.util.UUID;

/** POST /monthly-budgets: month plus at least one category limit, written atomically. No total field. */
public class MonthlyBudgetRequest extends FinanceRequest {
    public String month;
    public List<CategoryLimit> categories;

    public static class CategoryLimit extends FinanceRequest {
        public UUID categoryId;
        @JsonDeserialize(using = FinanceLongDeserializer.class)
        public Long limitMinor;
    }
}
