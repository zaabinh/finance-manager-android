package com.fintrack.api.service.finance;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

public class FinanceLongDeserializer extends JsonDeserializer<Long> {
    @Override public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) throw FinanceSupport.badRequest("Tiền phải là số nguyên VND");
        try { return parser.getBigIntegerValue().longValueExact(); }
        catch (ArithmeticException exception) { throw FinanceSupport.invalid("Số tiền vượt giới hạn"); }
    }
}
