package com.fintrack.api.dto.request.transaction;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fintrack.api.service.finance.FinanceLongDeserializer;
import com.fintrack.api.service.finance.FinanceRequest;
import java.util.UUID;

/** POST and PUT body; PUT accepts and ignores clientRequestId (contract §5). */
public class TransactionRequest extends FinanceRequest {
    public String transactionType;
    @JsonDeserialize(using = FinanceLongDeserializer.class) public Long amountMinor;
    public UUID categoryId;
    public UUID walletId;
    public UUID destinationWalletId;
    public String occurredAt;
    public String note;
    public String payee;
    public UUID clientRequestId;
}
