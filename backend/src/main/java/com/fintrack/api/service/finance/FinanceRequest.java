package com.fintrack.api.service.finance;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/** Only finance request DTOs inherit strict unknown/read-only field rejection. */
public abstract class FinanceRequest {
    @JsonAnySetter
    public final void unsupported(String field, Object value) {
        throw FinanceSupport.badRequest("Trường không được hỗ trợ: " + field);
    }
}
