package com.fintrack.api.service.finance;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

public class FinanceIntegerDeserializer extends JsonDeserializer<Integer> {
    @Override public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) throw FinanceSupport.invalid("Giá trị phải là số nguyên");
        try { return parser.getBigIntegerValue().intValueExact(); }
        catch (ArithmeticException exception) { throw FinanceSupport.invalid("Giá trị vượt giới hạn"); }
    }
}
