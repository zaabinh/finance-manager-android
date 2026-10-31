package com.fintrack.api.service;

import com.fintrack.api.dto.request.auth.LoginRequest;
import com.fintrack.api.dto.request.auth.RegisterRequest;
import com.fintrack.api.dto.response.auth.LoginResponse;

public interface AuthOperations {
    void register(RegisterRequest request);
    LoginResponse login(LoginRequest request);
}
