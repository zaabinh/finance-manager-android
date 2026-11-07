package com.fintrack.api.dto.request.wallet;
import com.fintrack.api.service.finance.FinanceRequest;
import com.fintrack.api.service.finance.FinanceBooleanDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
public class WalletStatusRequest extends FinanceRequest {
    @JsonDeserialize(using = FinanceBooleanDeserializer.class) public Boolean active;
}
