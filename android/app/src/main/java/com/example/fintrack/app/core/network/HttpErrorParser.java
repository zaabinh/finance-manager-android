package com.example.fintrack.app.core.network;

import com.example.fintrack.app.core.error.AppError;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Response;

public final class HttpErrorParser {
    private HttpErrorParser() { }
    public static AppError parse(Response<?> response) {
        try (ResponseBody body = response.errorBody()) {
            if (body != null) {
                JsonElement root = JsonParser.parseString(body.source().readUtf8());
                if (root.isJsonObject()) {
                    JsonObject object = root.getAsJsonObject();
                    List<AppError.FieldError> fields = new ArrayList<>();
                    JsonElement entries = object.get("fieldErrors");
                    if (entries != null && entries.isJsonArray()) for (JsonElement entry : entries.getAsJsonArray()) {
                        if (!entry.isJsonObject()) continue;
                        String name = string(entry.getAsJsonObject(), "field");
                        if (name != null) fields.add(new AppError.FieldError(name, string(entry.getAsJsonObject(), "message")));
                    }
                    String code = string(object, "errorCode");
                    return new AppError(AppError.Category.HTTP, response.code(), code == null ? "LOCAL_HTTP" : code,
                            fields, exactLong(object.get("shortfallMinor")), null);
                }
            }
        } catch (IOException | RuntimeException error) {
            return new AppError(AppError.Category.HTTP, response.code(), "LOCAL_HTTP", null, null, error);
        }
        return new AppError(AppError.Category.HTTP, response.code(), "LOCAL_HTTP", null, null, null);
    }
    public static AppError from(Throwable error) {
        return AppError.local(error instanceof IOException ? AppError.Category.NETWORK : AppError.Category.UNEXPECTED,
                error instanceof SocketTimeoutException ? "LOCAL_TIMEOUT" : error instanceof IOException
                        ? "LOCAL_NETWORK" : "LOCAL_UNEXPECTED", error);
    }
    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }
    private static Long exactLong(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return null;
        try { return value.getAsBigDecimal().longValueExact(); }
        catch (ArithmeticException | NumberFormatException ignored) { return null; }
    }
}
