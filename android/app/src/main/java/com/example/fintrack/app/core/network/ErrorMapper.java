package com.example.fintrack.app.core.network;

import android.content.Context;

import com.example.fintrack.R;

import java.io.IOException;
import java.net.SocketTimeoutException;

import retrofit2.Response;

public class ErrorMapper {

    public static String getErrorMessage(Context context, Throwable throwable) {
        if (context == null) {
            return "Không thể kết nối đến máy chủ";
        }
        if (throwable instanceof SocketTimeoutException) {
            return context.getString(R.string.error_timeout);
        } else if (throwable instanceof IOException) {
            return context.getString(R.string.error_connection);
        }
        return context.getString(R.string.error_unknown);
    }

    public static String getHttpErrorMessage(Context context, Response<?> response) {
        if (context == null) {
            return "Máy chủ gặp sự cố";
        }
        if (response == null) {
            return context.getString(R.string.error_server);
        }
        int code = response.code();
        if (code == 400) {
            return context.getString(R.string.error_bad_request);
        } else if (code == 401) {
            return context.getString(R.string.error_unauthorized);
        } else if (code == 403) {
            return context.getString(R.string.error_forbidden);
        } else if (code == 404) {
            return context.getString(R.string.error_not_found);
        } else if (code >= 500) {
            return context.getString(R.string.error_server);
        }
        return context.getString(R.string.error_unknown);
    }
}
