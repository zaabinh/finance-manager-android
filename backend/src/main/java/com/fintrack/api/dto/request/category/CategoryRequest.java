package com.fintrack.api.dto.request.category;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fintrack.api.service.finance.FinanceIntegerDeserializer;
import com.fintrack.api.service.finance.FinanceRequest;

public class CategoryRequest extends FinanceRequest {
    public String name;
    public String categoryType;
    public String iconKey;
    public String colorHex;
    @JsonDeserialize(using = FinanceIntegerDeserializer.class)
    public Integer sortOrder;
}
