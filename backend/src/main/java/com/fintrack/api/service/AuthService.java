package com.fintrack.api.service;

import com.fintrack.api.dto.request.auth.LoginRequest;
import com.fintrack.api.dto.request.auth.RegisterRequest;
import com.fintrack.api.dto.response.auth.LoginResponse;
import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.repository.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test")
public class AuthService implements AuthOperations {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        try {
            users.create(username, email, passwordEncoder.encode(request.password()));
        } catch (DuplicateKeyException exception) {
            throw new AppException(HttpStatus.CONFLICT, ErrorCode.REQUEST_ERROR, "Account already exists");
        }
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        var user = users.findByIdentifier(request.usernameOrEmail().trim())
                .filter(record -> "ACTIVE".equals(record.status()))
                .filter(record -> passwordEncoder.matches(request.password(), record.passwordHash()))
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED,
                        ErrorCode.AUTH_INVALID_CREDENTIALS, "Invalid credentials"));
        return LoginResponse.of(user.displayName(), user.username(), user.onboardingCompleted(),
                tokens.issue(user.id(), user.role()));
    }
}
