package com.fintrack.api.exception;

import org.springframework.http.HttpStatus;

/** Wrong OTP attempts must commit even though the API rejects the request. */
public class ChallengeRejectedException extends AppException {
    public ChallengeRejectedException(HttpStatus status, ErrorCode code, String message) { super(status, code, message); }
}
