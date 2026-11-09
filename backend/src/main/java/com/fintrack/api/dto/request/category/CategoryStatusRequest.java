package com.fintrack.api.dto.request.category;

import com.fintrack.api.service.finance.FinanceRequest;
import com.fintrack.api.service.finance.FinanceBooleanDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
public class CategoryStatusRequest extends FinanceRequest {
    @JsonDeserialize(using = FinanceBooleanDeserializer.class)
    public Boolean active;
}
