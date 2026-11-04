package com.fintrack.api.service.auth;

import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {
    public boolean isValid(String password) {
        return password != null && password.length() >= 8 && password.length() <= 64
                && password.getBytes(StandardCharsets.UTF_8).length <= 72
                && password.chars().anyMatch(c -> c >= 'a' && c <= 'z')
                && password.chars().anyMatch(c -> c >= 'A' && c <= 'Z')
                && password.chars().anyMatch(c -> c >= '0' && c <= '9')
                && password.chars().anyMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c))
                && password.chars().noneMatch(Character::isISOControl);
    }

    public void validate(String password) {
        if (!isValid(password)) throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                "Password must contain 8-64 characters, uppercase, lowercase, digit and special character; maximum 72 UTF-8 bytes");
    }
}
