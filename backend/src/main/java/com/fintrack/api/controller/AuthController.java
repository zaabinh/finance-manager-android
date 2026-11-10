package com.fintrack.api.controller;

import com.fintrack.api.dto.request.auth.*;
import com.fintrack.api.dto.response.ApiResponse;
import com.fintrack.api.dto.response.auth.LoginResponse;
import com.fintrack.api.dto.response.auth.TokenResponse;
import com.fintrack.api.service.auth.*;
import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.WebUtils;

@RestController
@RequestMapping("/api/v1/auth")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Authentication")
@io.swagger.v3.oas.annotations.responses.ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request or OTP", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.fintrack.api.dto.response.ApiErrorResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials or refresh token"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Unverified, locked or disabled account"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email or username already exists"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "Expired OTP"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit or OTP attempts exceeded"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Authentication rate limiting is temporarily unavailable")
})
public class AuthController {
    // __Secure- prefix: browsers reject the cookie unless it is Secure. Path limits it to auth endpoints.
    static final String REFRESH_COOKIE = "__Secure-refresh_token";
    private final AuthOperations auth;
    private final RateLimitingService limits;
    private final boolean refreshCookie;
    private final Duration refreshTtl;
    public AuthController(AuthOperations auth, RateLimitingService limits,
            @Value("${app.auth.refresh-cookie:false}") boolean refreshCookie,
            @Value("${app.auth.refresh-token-ttl:30d}") Duration refreshTtl) {
        this.auth = auth; this.limits = limits; this.refreshCookie = refreshCookie; this.refreshTtl = refreshTtl;
    }
    @PostMapping("/register")
    @io.swagger.v3.oas.annotations.Operation(summary = "Register an unverified account and email a code")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Account created; empty body")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
        ipLimit("register", request, 10, Duration.ofHours(1));
        auth.register(body);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
    @PostMapping("/login")
    @io.swagger.v3.oas.annotations.Operation(summary = "Authenticate with username/email and password; starts a new token family")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        ipLimit("login-requests", request, 100, Duration.ofMinutes(15));
        var login = auth.login(body, ClientMetadata.from(request));
        return new ApiResponse<>(true, "Login successful",
                new LoginResponse(login.onboardingCompleted(), deliver(login.token(), response)), 200);
    }
    @PostMapping("/refresh")
    @io.swagger.v3.oas.annotations.Operation(summary = "Rotate a refresh token (body or cookie) and issue an access token")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody(required = false) RefreshTokenRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        ipLimit("refresh", request, 20, Duration.ofMinutes(1));
        String raw = presentedRefreshToken(body, request);
        if (raw == null) throw new AppException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_REFRESH_TOKEN_INVALID, "Invalid refresh token");
        return new ApiResponse<>(true, "Token refreshed", deliver(auth.refresh(raw, ClientMetadata.from(request)), response), 200);
    }
    @PostMapping("/logout")
    @io.swagger.v3.oas.annotations.Operation(summary = "Revoke the device's token family")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Session revoked; empty body")
    public ResponseEntity<Void> logout(@Valid @RequestBody(required = false) RefreshTokenRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        ipLimit("logout", request, 60, Duration.ofMinutes(1));
        String raw = presentedRefreshToken(body, request);
        if (raw != null) auth.logout(raw);
        if (refreshCookie) response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/change-password")
    @io.swagger.v3.oas.annotations.Operation(summary = "Change password; signs out every device and returns a new token pair")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    public ApiResponse<TokenResponse> changePassword(@AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ChangePasswordRequest body, HttpServletRequest request, HttpServletResponse response) {
        ipLimit("change-password", request, 20, Duration.ofMinutes(15));
        var tokens = auth.changePassword(principal.user().id(), body, ClientMetadata.from(request));
        return new ApiResponse<>(true, "Password changed", deliver(tokens, response), 200);
    }
    @PostMapping("/verify-email")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Email verified; empty body")
    public ResponseEntity<Void> verify(@Valid @RequestBody VerifyEmailRequest body, HttpServletRequest request) {
        challengeLimit("verify", body.email(), request);
        auth.verifyEmail(body);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/resend-verification")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Request accepted regardless of account existence; empty body")
    public ResponseEntity<Void> resend(@Valid @RequestBody ResendOtpRequest body, HttpServletRequest request) {
        emailLimit("verification", body.email(), request);
        auth.resendVerification(body);
        return ResponseEntity.accepted().build();
    }
    @PostMapping("/forgot-password")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Request accepted regardless of account existence; empty body")
    public ResponseEntity<Void> forgot(@Valid @RequestBody ResendOtpRequest body, HttpServletRequest request) {
        emailLimit("reset", body.email(), request);
        auth.forgotPassword(body);
        return ResponseEntity.accepted().build();
    }
    @PostMapping("/reset-password")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Password reset and all refresh sessions revoked; empty body")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest body, HttpServletRequest request) {
        challengeLimit("reset", body.email(), request);
        auth.resetPassword(body);
        return ResponseEntity.noContent().build();
    }
    /** Mobile clients get the refresh token in JSON; with app.auth.refresh-cookie it goes only into the cookie. */
    private TokenResponse deliver(TokenResponse tokens, HttpServletResponse response) {
        if (!refreshCookie) return tokens;
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(tokens.refreshToken(), refreshTtl).toString());
        return new TokenResponse(tokens.accessToken(), null, tokens.tokenType(), tokens.expiresIn());
    }
    private String presentedRefreshToken(RefreshTokenRequest body, HttpServletRequest request) {
        if (body != null && body.refreshToken() != null) return body.refreshToken();
        var cookie = refreshCookie ? WebUtils.getCookie(request, REFRESH_COOKIE) : null;
        return cookie == null || cookie.getValue().isBlank() || cookie.getValue().length() > 128 ? null : cookie.getValue();
    }
    private static ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value).httpOnly(true).secure(true).sameSite("Strict")
                .path("/api/v1/auth").maxAge(maxAge).build();
    }
    private void ipLimit(String scope, HttpServletRequest request, int count, Duration window) {
        limits.checkAndRecord(scope + "-ip", request.getRemoteAddr(), count, window);
    }
    private void challengeLimit(String scope, String email, HttpServletRequest request) {
        ipLimit(scope, request, 60, Duration.ofMinutes(15));
        limits.checkAndRecord(scope + "-email", email, 20, Duration.ofMinutes(15));
    }
    private void emailLimit(String scope, String email, HttpServletRequest request) {
        // Same limits and responses for registered and unknown addresses.
        ipLimit(scope + "-send", request, 20, Duration.ofHours(1));
        limits.checkAndRecord(scope + "-hour", email, 3, Duration.ofHours(1));
        limits.checkAndRecord(scope + "-cooldown", email, 1, Duration.ofSeconds(60));
    }
}
