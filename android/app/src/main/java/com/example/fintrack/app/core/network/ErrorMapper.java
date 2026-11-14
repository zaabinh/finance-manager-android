package com.example.fintrack.app.core.network;

import android.content.Context;
import com.example.fintrack.R;
import com.example.fintrack.app.core.error.AppError;

/** UI localization only. Parsing and control flow never depend on display text. */
public final class ErrorMapper {
    private ErrorMapper() { }
    public static String text(Context context, AppError error) {
        if (error == null) return context.getString(R.string.error_unknown);
        if (error.code != null) switch (error.code) {

                            case "AUTH_EMAIL_NOT_VERIFIED":
                                return context.getString(R.string.error_email_not_verified);

                            case "AUTH_USERNAME_ALREADY_EXISTS":
                                return context.getString(R.string.error_auth_username_already_exists);

                            case "AUTH_EMAIL_ALREADY_EXISTS":
                                return context.getString(R.string.error_auth_email_already_exists);

                            case "AUTH_USER_LOCKED":
                                return context.getString(R.string.error_auth_user_locked);

                            case "AUTH_USER_DISABLED":
                                return context.getString(R.string.error_auth_user_disabled);

                            case "AUTH_INVALID_CREDENTIALS":
                            case "AUTH_USERNAME_NOT_EXIST":
                                return context.getString(R.string.error_auth_invalid_credentials);

                            case "AUTH_OTP_INVALID":
                                return context.getString(R.string.error_auth_otp_invalid);

                            case "AUTH_OTP_EXPIRED":
                                return context.getString(R.string.error_auth_otp_expired);

                            case "AUTH_RATE_LIMITED":
                            case "AUTH_OTP_ATTEMPTS_EXCEEDED":
                                return context.getString(R.string.error_auth_rate_limited);

                            case "AUTH_ACCESS_TOKEN_INVALID":
                            case "AUTH_REFRESH_TOKEN_INVALID":
                                return context.getString(R.string.error_unauthorized);

            case "LOCAL_TIMEOUT": return context.getString(R.string.error_timeout);
        }
        if (error.category == AppError.Category.NETWORK) return context.getString(R.string.error_connection);
        int status = error.httpStatus == null ? 0 : error.httpStatus;
        int label = status == 400 || status == 422 ? R.string.error_bad_request
                : status == 401 ? R.string.error_unauthorized : status == 403 ? R.string.error_forbidden
                : status == 404 ? R.string.error_not_found : status >= 500 ? R.string.error_server : R.string.error_unknown;
        return context.getString(label);
    }
}
