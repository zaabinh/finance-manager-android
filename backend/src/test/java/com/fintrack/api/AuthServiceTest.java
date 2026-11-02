package com.fintrack.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fintrack.api.config.AuthProperties;
import com.fintrack.api.dto.request.auth.*;
import com.fintrack.api.exception.*;
import com.fintrack.api.repository.*;
import com.fintrack.api.repository.VerificationChallengeRepository.*;
import com.fintrack.api.security.*;
import com.fintrack.api.service.auth.*;
import com.fintrack.api.service.auth.EmailVerificationService.CodeEmail;
import com.fintrack.api.service.notification.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final VerificationChallengeRepository challenges = mock(VerificationChallengeRepository.class);
    private final RefreshTokenRotatingService refreshTokens = mock(RefreshTokenRotatingService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-02T00:00:00Z"), ZoneOffset.UTC);
    private final RateLimitingService limits = new RateLimitingService(clock);
    private final UUID id = UUID.randomUUID();
    private final ClientMetadata client = new ClientMetadata("127.0.0.1", null);
    private AuthService service;
    private UserRepository.UserRecord user;
    @BeforeEach void setup() {
        var provider = new DaoAuthenticationProvider(new CustomUserDetailsService(users));
        provider.setPasswordEncoder(encoder);
        var properties = new AuthProperties("test", Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofMinutes(10), null, null, Duration.ofSeconds(20));
        var verification = new EmailVerificationService(challenges, encoder, events, properties, clock,
                mock(EmailSenderService.class), mock(EmailTemplateRenderer.class));
        service = new AuthService(users, verification, encoder, refreshTokens, new PasswordPolicy(),
                new ProviderManager(provider), limits);
        user = new UserRepository.UserRecord(id, "an.nguyen", "an@example.com", encoder.encode("Strong@123"),
                "ACTIVE", "USER", null, false, true);
    }
    @Test void registrationHashesPasswordAndOtpAndPublishesDeliveryEvent() {
        when(users.create(anyString(), anyString(), anyString())).thenReturn(id);
        service.register(new RegisterRequest(" an.nguyen ", " AN@EXAMPLE.COM ", "Strong@123"));
        var hash = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(users).create(eq("an.nguyen"), eq("an@example.com"), hash.capture());
        assertThat(encoder.matches("Strong@123", hash.getValue())).isTrue();
        var event = org.mockito.ArgumentCaptor.forClass(CodeEmail.class);
        verify(events).publishEvent(event.capture());
        var otpHash = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(challenges).create(eq(id), eq("an@example.com"), eq(Purpose.EMAIL_VERIFICATION), otpHash.capture(), any(), any());
        assertThat(event.getValue().code()).matches("[0-9]{6}");
        assertThat(encoder.matches(event.getValue().code(), otpHash.getValue())).isTrue();
    }
    @Test void springSecurityUsesGenericErrorForUnknownOrWrongCredentialsAndLimitsFailures() {
        when(users.findByIdentifier(anyString())).thenReturn(Optional.empty());
        for (int i = 0; i < 5; i++) assertThatThrownBy(() -> service.login(new LoginRequest("missing", "Strong@123"), client))
                .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
        assertThatThrownBy(() -> service.login(new LoginRequest("missing", "Strong@123"), client))
                .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_RATE_LIMITED));
    }
    @Test void verifiedLoginUsesPrincipalFromSpringSecurity() {
        when(users.findByIdentifier("an.nguyen")).thenReturn(Optional.of(user));
        when(users.lockById(id)).thenReturn(Optional.of(user));
        assertThat(service.login(new LoginRequest("an.nguyen", "Strong@123"), client).displayName()).isEqualTo("an.nguyen");
        verify(refreshTokens).generateTokenPair(id, "USER", null, client);
    }
    @Test void unverifiedUserCannotLogin() {
        var unverified = new UserRepository.UserRecord(id, user.username(), user.email(), user.passwordHash(), "ACTIVE", "USER", null, false, false);
        when(users.findByIdentifier("an.nguyen")).thenReturn(Optional.of(unverified));
        when(users.lockById(id)).thenReturn(Optional.of(unverified));
        assertThatThrownBy(() -> service.login(new LoginRequest("an.nguyen", "Strong@123"), client))
                .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_EMAIL_NOT_VERIFIED));
        verifyNoInteractions(refreshTokens);
    }
    @Test void wrongOtpRecordsFailureAndNeverConsumesOrChangesPassword() {
        stubChallenge(4, clock.instant().plusSeconds(60));
        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest(user.email(), "000000", "NewStrong@456")))
                .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_OTP_ATTEMPTS_EXCEEDED));
        verify(challenges).fail(id);
        verify(users, never()).updatePassword(any(), any());
        verify(challenges, never()).consume(any(), any());
    }
    @Test void resetConsumesCodeAndRevokesEveryRefreshSession() {
        stubChallenge(0, clock.instant().plusSeconds(60));
        service.resetPassword(new ResetPasswordRequest(user.email(), "123456", "NewStrong@456"));
        verify(challenges).consume(id, clock.instant());
        verify(refreshTokens).revokeAllUserSessions(id, RefreshSessionRepository.RevokeReason.PASSWORD_RESET);
        var hash = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(users).updatePassword(eq(id), hash.capture());
        assertThat(encoder.matches("NewStrong@456", hash.getValue())).isTrue();
    }
    @Test void expiredAndExhaustedCodesCannotBeConsumed() {
        stubChallenge(0, clock.instant());
        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest(user.email(), "123456", "NewStrong@456")))
                .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_OTP_EXPIRED));
        stubChallenge(5, clock.instant().plusSeconds(60));
        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest(user.email(), "123456", "NewStrong@456")))
                .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_OTP_ATTEMPTS_EXCEEDED));
        verify(challenges, never()).consume(any(), any());
    }
    @Test void forgotPasswordForUnknownAddressDoesNotSendEmail() {
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        assertThatCode(() -> service.forgotPassword(new ResendOtpRequest("missing@example.com"))).doesNotThrowAnyException();
        verifyNoInteractions(events);
    }
    @Test void changePasswordRevokesEverySessionThenStartsNewFamilyForCaller() {
        when(users.lockById(id)).thenReturn(Optional.of(user));
        service.changePassword(id, new ChangePasswordRequest("Strong@123", "NewStrong@456"), client);
        var order = inOrder(users, refreshTokens);
        order.verify(users).updatePassword(eq(id), argThat(hash -> encoder.matches("NewStrong@456", hash)));
        order.verify(refreshTokens).revokeAllUserSessions(id, RefreshSessionRepository.RevokeReason.PASSWORD_CHANGED);
        order.verify(refreshTokens).generateTokenPair(id, "USER", null, client);
    }
    @Test void changePasswordRejectsWrongCurrentOrUnchangedPasswordWithoutTouchingSessions() {
        when(users.lockById(id)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.changePassword(id, new ChangePasswordRequest("Wrong@123", "NewStrong@456"), client))
                .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
        assertThatThrownBy(() -> service.changePassword(id, new ChangePasswordRequest("Strong@123", "Strong@123"), client))
                .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(users, never()).updatePassword(any(), any());
        verifyNoInteractions(refreshTokens);
    }
    private void stubChallenge(int attempts, Instant expiry) {
        when(users.findByEmail(user.email())).thenReturn(Optional.of(user));
        when(users.lockById(id)).thenReturn(Optional.of(user));
        when(challenges.lockLatest(id, Purpose.PASSWORD_RESET)).thenReturn(Optional.of(new Challenge(id, encoder.encode("123456"), expiry, attempts)));
    }
}
