package com.example.fintrack.app.core.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Lớp DTO nhận dữ liệu phản hồi lỗi chuẩn từ Backend Spring Boot (ApiErrorResponse)
 */
public class ApiErrorResponse {

    @SerializedName("timestamp")
    private String timestamp;

    @SerializedName("status")
    private int status;

    @SerializedName("errorCode")
    private String errorCode;

    @SerializedName("message")
    private String message;

    @SerializedName("path")
    private String path;

    @SerializedName("fieldErrors")
    private List<FieldError> fieldErrors;

    public static class FieldError {
        @SerializedName("field")
        private String field;

        @SerializedName("message")
        private String message;

        public String getField() { return field; }
        public String getMessage() { return message; }
    }

    public String getTimestamp() { return timestamp; }
    public int getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
    public String getMessage() { return message; }
    public String getPath() { return path; }
    public List<FieldError> getFieldErrors() { return fieldErrors; }
}
