package com.fintrack.api.service.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

public final class NotificationJson {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private NotificationJson() {}
    public static String write(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (Exception error) { throw new IllegalArgumentException("Invalid notification payload", error); }
    }
    public static Map<String, Object> read(String value) {
        try { return MAPPER.readValue(value, new TypeReference<>() {}); }
        catch (Exception error) { throw new IllegalArgumentException("Invalid stored notification payload", error); }
    }
    public static Map<String, String> strings(String value) {
        try { return MAPPER.readValue(value, new TypeReference<>() {}); }
        catch (Exception error) { throw new IllegalArgumentException("Invalid stored template variables", error); }
    }
}
