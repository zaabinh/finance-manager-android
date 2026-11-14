package com.example.fintrack.app.core.network;

import static org.junit.Assert.assertEquals;

import com.example.fintrack.R;
import com.example.fintrack.app.core.error.AppError;
import java.util.Collections;
import org.junit.Test;

/** Which message each error shows; the decision is pure, so no Android Context is needed. */
public class ErrorMapperTest {
    private static AppError http(int status, String code) {
        return new AppError(AppError.Category.HTTP, status, code, Collections.emptyList(), null, null);
    }

    @Test public void backendCodeWinsOverTheHttpStatus() {
        assertEquals(R.string.error_email_not_verified, ErrorMapper.message(http(403, "AUTH_EMAIL_NOT_VERIFIED")));
        assertEquals(R.string.error_auth_invalid_credentials, ErrorMapper.message(http(401, "AUTH_INVALID_CREDENTIALS")));
        // An unknown username gets the same message as a wrong password.
        assertEquals(R.string.error_auth_invalid_credentials, ErrorMapper.message(http(404, "AUTH_USERNAME_NOT_EXIST")));
        assertEquals(R.string.error_auth_rate_limited, ErrorMapper.message(http(429, "AUTH_OTP_ATTEMPTS_EXCEEDED")));
        assertEquals(R.string.error_unauthorized, ErrorMapper.message(http(401, "AUTH_REFRESH_TOKEN_INVALID")));
    }

    @Test public void transportFailuresMapByCodeAndCategory() {
        assertEquals(R.string.error_timeout, ErrorMapper.message(HttpErrorParser.from(new java.net.SocketTimeoutException())));
        assertEquals(R.string.error_connection, ErrorMapper.message(HttpErrorParser.from(new java.io.IOException())));
    }

    @Test public void unknownCodesFallBackToTheHttpStatus() {
        assertEquals(R.string.error_bad_request, ErrorMapper.message(http(400, "FUTURE_CODE")));
        assertEquals(R.string.error_bad_request, ErrorMapper.message(http(422, null)));
        assertEquals(R.string.error_unauthorized, ErrorMapper.message(http(401, null)));
        assertEquals(R.string.error_forbidden, ErrorMapper.message(http(403, null)));
        assertEquals(R.string.error_not_found, ErrorMapper.message(http(404, null)));
        assertEquals(R.string.error_server, ErrorMapper.message(http(503, null)));
        assertEquals(R.string.error_unknown, ErrorMapper.message(http(409, null)));
        assertEquals(R.string.error_unknown, ErrorMapper.message(null));
    }
}
