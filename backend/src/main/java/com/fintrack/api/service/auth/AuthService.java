package com.fintrack.api.service.auth;

import com.fintrack.api.dto.request.auth.*;
import com.fintrack.api.dto.response.auth.LoginResponse;
import com.fintrack.api.dto.response.auth.TokenResponse;
import com.fintrack.api.exception.*;
import com.fintrack.api.repository.*;
import com.fintrack.api.repository.RefreshSessionRepository.RevokeReason;
import com.fintrack.api.repository.VerificationChallengeRepository.Purpose;
import com.fintrack.api.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

import java.util.Locale;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test")
@RequiredArgsConstructor
public class AuthService implements AuthOperations {
    private final UserRepository users;
    private final EmailVerificationService verification;
    private final PasswordEncoder encoder;
    private final RefreshTokenRotatingService refreshTokens;
    private final PasswordPolicy policy;
    private final AuthenticationManager authentication;
    private final RateLimitingService limiter;

    @Override @Transactional
    public void register(RegisterRequest request) {
        String username = request.username().trim();
        String email = normalize(request.email());
        // Validate password
        policy.validate(request.password());
        // Validate username
        if (!username.matches("[A-Za-z0-9._-]{3,50}")) 
            throw new AppException(
                HttpStatus.BAD_REQUEST, 
                ErrorCode.VALIDATION_ERROR, 
                "Username must contain 3-50 letters, digits, dots, underscores or hyphens"
            );
        // Check for duplicate user
        if (users.emailExists(email)) 
            throw conflict(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS);
        if (users.usernameExists(username)) 
            throw conflict(ErrorCode.AUTH_USERNAME_ALREADY_EXISTS);
        UUID id;
        try { 
            id = users.create(username, email, encoder.encode(request.password())); 
        }
        catch (DuplicateKeyException exception) {
            // Read only the constraint name; never expose database messages containing identity values.
            String detail = exception.getMostSpecificCause().getMessage();
            throw conflict(detail != null && 
                (detail.contains("users_username_key") 
                || detail.contains("ux_users_username"))
                    ? ErrorCode.AUTH_USERNAME_ALREADY_EXISTS : ErrorCode.AUTH_EMAIL_ALREADY_EXISTS);
        }
        // Send verification email
        verification.issue(id, email, Purpose.EMAIL_VERIFICATION, username);
    }
    @Override @Transactional
    public LoginResponse login(LoginRequest request, ClientMetadata client) {
        String identifier = request.usernameOrEmail().trim();
        // Rate limiter checking
        limiter.checkLoginAllowed(identifier, client);
        AuthenticatedUser principal;
        try {
            // Authenticate credential and get user principal
            principal = (AuthenticatedUser) authentication.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                        identifier, 
                        request.password()
                    )).getPrincipal();
        } catch (LockedException exception) {
            // User locked
            throw new AppException(
                HttpStatus.FORBIDDEN, 
                ErrorCode.AUTH_USER_LOCKED, 
                "Account is locked");
        } catch (DisabledException exception) {
            // User disabled
            throw new AppException(
                HttpStatus.FORBIDDEN, 
                ErrorCode.AUTH_USER_DISABLED, 
                "Account is disabled");
        } catch (BadCredentialsException exception) {
            // Invalid credential
            limiter.recordLoginFailure(identifier, client);
            throw new AppException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_INVALID_CREDENTIALS, "Username or password invalid");
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException exception) {
            // User not found
            limiter.recordLoginFailure(identifier, client);
            throw new AppException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_USERNAME_NOT_EXIST, "User not found");
        }

        var user = users.lockById(principal.id())
                        .orElseThrow(() -> new AppException(
                            HttpStatus.UNAUTHORIZED, 
                            ErrorCode.AUTH_INVALID_CREDENTIALS,
                            "Invalid credentials"));
        
        // A concurrent reset must not allow login with the password checked before the reset committed.
        if (!encoder.matches(request.password(), user.passwordHash())) {
            limiter.recordLoginFailure(identifier, client);
            throw new AppException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_INVALID_CREDENTIALS, "Invalid credentials");
        }

        // Check for user status
        if (!"ACTIVE".equals(user.status())) throw new AppException(
            HttpStatus.FORBIDDEN,
            "LOCKED".equals(user.status()) ? ErrorCode.AUTH_USER_LOCKED : ErrorCode.AUTH_USER_DISABLED, 
            "Account unavailable");
        
        // Check for verified email
        if (!user.emailVerified()) 
            throw new AppException(
                HttpStatus.FORBIDDEN, 
                ErrorCode.AUTH_EMAIL_NOT_VERIFIED, 
                "Email is not verified"
            );
        limiter.clearLoginFailures(identifier, client);
        return LoginResponse.of(user.displayName(), user.username(), user.onboardingCompleted(), refreshTokens.generateTokenPair(user.id(), user.role(), null, client));
    }

    @Override 
    public TokenResponse refresh(String raw, ClientMetadata client) { 
        return refreshTokens.rotateRefreshToken(raw, client); 
    }

    @Override 
    public void logout(String raw) { 
        refreshTokens.revokeByToken(raw); 
    }

    @Override @Transactional(noRollbackFor = ChallengeRejectedException.class)
    public void verifyEmail(VerifyEmailRequest request) {
        var user = lockEmail(request.email());
        if (user.emailVerified()) return;
        verification.verify(user.id(), Purpose.EMAIL_VERIFICATION, request.otp());
        users.verify(user.id());
    }

    @Override @Transactional
    public void resendVerification(ResendOtpRequest request) { 
        sendIfEligible(request.email(), Purpose.EMAIL_VERIFICATION); 
    }

    @Override @Transactional
    public void forgotPassword(ResendOtpRequest request) { 
        sendIfEligible(request.email(), Purpose.PASSWORD_RESET); 
    }
    
    private void sendIfEligible(String email, Purpose purpose) {
        users.findByEmail(normalize(email))
                .flatMap(user -> users.lockById(user.id()))
                .ifPresent(user -> {
            if (!"ACTIVE".equals(user.status())) return;
            if (purpose == Purpose.EMAIL_VERIFICATION && user.emailVerified()) return;
            if (purpose == Purpose.PASSWORD_RESET && !user.emailVerified()) return;
            String name = user.displayName() == null || user.displayName().isBlank() ? user.username() : user.displayName();
            verification.issue(user.id(), user.email(), purpose, name);
        });
    }
    @Override @Transactional(noRollbackFor = ChallengeRejectedException.class)
    public void resetPassword(ResetPasswordRequest request) {
        policy.validate(request.newPassword());
        var user = lockEmail(request.email());
        if (!"ACTIVE".equals(user.status()) || !user.emailVerified()) 
            throw rejected(HttpStatus.BAD_REQUEST, ErrorCode.AUTH_OTP_INVALID, "Invalid code");
        verification.verify(user.id(), Purpose.PASSWORD_RESET, request.otp());
        users.updatePassword(user.id(), encoder.encode(request.newPassword()));
        refreshTokens.revokeAllUserSessions(user.id(), RevokeReason.PASSWORD_RESET);
    }
    @Override @Transactional
    public TokenResponse changePassword(UUID userId, ChangePasswordRequest request, ClientMetadata client) {
        // Separate bucket from login: a stolen access token must not allow unlimited current-password guesses.
        String limiterKey = "change-password:" + userId;
        limiter.checkLoginAllowed(limiterKey, client);
        var user = users.lockById(userId).orElseThrow(() -> new AppException(
                HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_ACCESS_TOKEN_INVALID, "Authentication required"));
        if (!encoder.matches(request.currentPassword(), user.passwordHash())) {
            limiter.recordLoginFailure(limiterKey, client);
            // 400, not 401: the access token is valid and clients treat 401 as "session expired".
            throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.AUTH_INVALID_CREDENTIALS, "Current password is incorrect");
        }
        policy.validate(request.newPassword());
        if (encoder.matches(request.newPassword(), user.passwordHash()))
            throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, "New password must differ from the current password");
        limiter.clearLoginFailures(limiterKey, client);
        users.updatePassword(user.id(), encoder.encode(request.newPassword()));
        refreshTokens.revokeAllUserSessions(user.id(), RevokeReason.PASSWORD_CHANGED);
        // Every device is signed out; only the device that changed the password gets a fresh family.
        return refreshTokens.generateTokenPair(user.id(), user.role(), null, client);
    }
    private UserRepository.UserRecord lockEmail(String email) {
        return users.findByEmail(normalize(email))
        .flatMap(user -> users.lockById(user.id()))
        .orElseThrow(() -> rejected(HttpStatus.BAD_REQUEST, ErrorCode.AUTH_OTP_INVALID, "Invalid code"));
    }
    private static String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private static AppException conflict(ErrorCode code) { return new AppException(HttpStatus.CONFLICT, code, "Account already exists"); }
    private static ChallengeRejectedException rejected(HttpStatus status, ErrorCode code, String message) {
        return new ChallengeRejectedException(status, code, message);
    }
}
