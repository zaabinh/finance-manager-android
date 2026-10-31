package com.fintrack.api.service;

import com.fintrack.api.dto.request.auth.LoginRequest;
import com.fintrack.api.dto.request.auth.RegisterRequest;
import com.fintrack.api.dto.response.auth.LoginResponse;
import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Profile("test")
public class TestAuthService implements AuthOperations {
    @Override public void register(RegisterRequest request) { }

    @Override
    public LoginResponse login(LoginRequest request) {
        throw new AppException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_INVALID_CREDENTIALS, "Invalid credentials");
    }
}
