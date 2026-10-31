package com.fintrack.api.controller;

import com.fintrack.api.dto.request.auth.LoginRequest;
import com.fintrack.api.dto.request.auth.RegisterRequest;
import com.fintrack.api.dto.response.auth.LoginResponse;
import com.fintrack.api.service.AuthOperations;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthOperations auth;

    public AuthController(AuthOperations auth) { this.auth = auth; }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        auth.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }
}
