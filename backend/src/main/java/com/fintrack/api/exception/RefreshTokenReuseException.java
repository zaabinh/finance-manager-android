package com.fintrack.api.exception;

import org.springframework.http.HttpStatus;

/**
 * A rotated refresh token was replayed. The family revocation must commit even though the request fails.
 * Clients see the same generic 401 as any invalid refresh token, so attackers learn nothing.
 */
public class RefreshTokenReuseException extends AppException {
    public RefreshTokenReuseException() {
        super(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_REFRESH_TOKEN_INVALID, "Invalid refresh token");
    }
}
