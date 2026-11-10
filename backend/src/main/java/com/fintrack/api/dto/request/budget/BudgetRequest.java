package com.fintrack.api.dto.request.budget;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fintrack.api.service.finance.FinanceLongDeserializer;
import com.fintrack.api.service.finance.FinanceRequest;
import java.util.UUID;

public class BudgetRequest extends FinanceRequest {
    public UUID categoryId;
    public String month;
    @JsonDeserialize(using = FinanceLongDeserializer.class)
    public Long limitMinor;
}
