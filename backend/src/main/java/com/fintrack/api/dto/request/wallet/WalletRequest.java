package com.fintrack.api.dto.request.wallet;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fintrack.api.service.finance.FinanceRequest;
import com.fintrack.api.service.finance.FinanceLongDeserializer;
import com.fintrack.api.service.finance.FinanceIntegerDeserializer;
import com.fintrack.api.service.finance.FinanceBooleanDeserializer;

public class WalletRequest extends FinanceRequest {
    public String name;
    public String walletType;
    @JsonDeserialize(using = FinanceLongDeserializer.class) public Long openingBalanceMinor;
    @JsonDeserialize(using = FinanceBooleanDeserializer.class) public Boolean includeInAssets;
    @JsonDeserialize(using = FinanceIntegerDeserializer.class) public Integer sortOrder;
}
