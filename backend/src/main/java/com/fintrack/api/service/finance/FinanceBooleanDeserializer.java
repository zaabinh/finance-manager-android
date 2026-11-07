package com.fintrack.api.service.finance;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

public class FinanceBooleanDeserializer extends JsonDeserializer<Boolean> {
    @Override public Boolean deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.hasToken(JsonToken.VALUE_TRUE)) return true;
        if (parser.hasToken(JsonToken.VALUE_FALSE)) return false;
        throw FinanceSupport.invalid("Giá trị phải là boolean");
    }
}
