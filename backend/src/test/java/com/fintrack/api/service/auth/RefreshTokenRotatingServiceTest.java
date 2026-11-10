package com.fintrack.api.service.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fintrack.api.config.AuthProperties;
import com.fintrack.api.entity.enums.UserRole;
import com.fintrack.api.entity.enums.UserStatus;
import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.exception.RefreshTokenReuseException;
import com.fintrack.api.repository.RefreshSessionRepository;
import com.fintrack.api.repository.RefreshSessionRepository.RevokeReason;
import com.fintrack.api.repository.RefreshSessionRepository.Session;
import com.fintrack.api.repository.UserRepository;
import java.security.GeneralSecurityException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RefreshTokenRotatingServiceTest {
    private final RefreshSessionRepository sessions = mock(RefreshSessionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final TokenService tokens = mock(TokenService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-02T00:00:00Z"), ZoneOffset.UTC);
    private final Instant now = clock.instant();
    private final AuthProperties properties = new AuthProperties("test", Duration.ofMinutes(15), Duration.ofDays(30),
            Duration.ofMinutes(10), null, null, Duration.ofSeconds(20));
    private final ClientMetadata client = new ClientMetadata("203.0.113.7", "agent\r\nFAKE LOG LINE");
    private final UUID userId = UUID.randomUUID();
    private final UUID family = UUID.randomUUID();
    private final UUID oldId = UUID.randomUUID();
    private final UUID successorId = UUID.randomUUID();
    private RefreshTokenRotatingService service;

    @BeforeEach void setup() {
        service = new RefreshTokenRotatingService(sessions, users, tokens, properties, clock);
        var user = new UserRepository.UserRecord(userId, "user", "user@example.com", "hash", UserStatus.ACTIVE, UserRole.USER, null, false, true);
        when(users.lockById(userId)).thenReturn(Optional.of(user));
        when(tokens.createAccessToken(userId, UserRole.USER)).thenReturn("access");
        when(sessions.create(any(), any(), any(), any(), anyBoolean(), any())).thenReturn(successorId);
    }

    @Test void loginStartsNewFamilyAndStoresOnlyTheHash() {
        var pair = service.generateTokenPair(userId, UserRole.USER, null, client);
        var familyId = ArgumentCaptor.forClass(UUID.class);
        verify(sessions).create(eq(userId), familyId.capture(), eq(TokenHash.sha256(pair.refreshToken())),
                eq(now.plus(Duration.ofDays(30))), eq(true), eq(client));
        assertThat(familyId.getValue()).isNotNull();
        assertThat(pair.refreshToken()).hasSize(43).isNotEqualTo(TokenHash.sha256(pair.refreshToken()));
        assertThat(pair.accessToken()).isEqualTo("access");
    }

    @Test void loginWithoutRememberMeGetsAShortSessionThatRotationCannotExtend() {
        service.generateTokenPair(userId, UserRole.USER, null, client, false);
        verify(sessions).create(eq(userId), any(), any(), eq(now.plus(RefreshTokenRotatingService.UNREMEMBERED_TTL)), eq(false), eq(client));

        stub("short", new Session(oldId, userId, family, now.plus(Duration.ofHours(1)), null, null, null, null, false));
        service.rotateRefreshToken("short", client);
        // The successor stays short-lived and keeps the flag, not the 30-day default.
        verify(sessions, times(2)).create(eq(userId), any(), any(), eq(now.plus(RefreshTokenRotatingService.UNREMEMBERED_TTL)), eq(false), eq(client));
    }

    @Test void rotationKeepsFamilyRevokesOldAndSealsSuccessorForGracePeriod() throws Exception {
        stub("old", session(oldId, null, null, null, null));
        var pair = service.rotateRefreshToken("old", client);
        verify(sessions).create(eq(userId), eq(family), eq(TokenHash.sha256(pair.refreshToken())), any(), eq(true), eq(client));
        var sealed = ArgumentCaptor.forClass(byte[].class);
        verify(sessions).rotate(eq(oldId), eq(successorId), sealed.capture(), eq(now));
        assertThat(SuccessorTokenCipher.open("old", oldId, sealed.getValue())).isEqualTo(pair.refreshToken());
    }

    @Test void concurrentRefreshInsideGracePeriodGetsTheSameSuccessor() {
        byte[] sealed = SuccessorTokenCipher.seal("old", oldId, "successor-raw");
        stub("old", session(oldId, now.minusSeconds(5), RevokeReason.ROTATED, successorId, sealed));
        when(sessions.findById(successorId)).thenReturn(Optional.of(session(successorId, null, null, null, null)));
        var pair = service.rotateRefreshToken("old", client);
        assertThat(pair.refreshToken()).isEqualTo("successor-raw");
        assertThat(pair.accessToken()).isEqualTo("access");
        verify(sessions, never()).create(any(), any(), any(), any(), anyBoolean(), any());
        verify(sessions, never()).revokeFamily(any(), any(), any());
    }

    @Test void replayAfterGracePeriodRevokesTheWholeFamily() {
        byte[] sealed = SuccessorTokenCipher.seal("old", oldId, "successor-raw");
        stub("old", session(oldId, now.minusSeconds(21), RevokeReason.ROTATED, successorId, sealed));
        when(sessions.findById(successorId)).thenReturn(Optional.of(session(successorId, null, null, null, null)));
        assertThatThrownBy(() -> service.rotateRefreshToken("old", client))
                .isInstanceOfSatisfying(RefreshTokenReuseException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_INVALID));
        verify(sessions).revokeFamily(family, RevokeReason.REUSE_DETECTED, now);
        verify(sessions, never()).create(any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test void replayInsideGracePeriodIsReuseOnceSuccessorWasUsed() {
        byte[] sealed = SuccessorTokenCipher.seal("old", oldId, "successor-raw");
        stub("old", session(oldId, now.minusSeconds(5), RevokeReason.ROTATED, successorId, sealed));
        when(sessions.findById(successorId)).thenReturn(Optional.of(
                session(successorId, now.minusSeconds(1), RevokeReason.ROTATED, UUID.randomUUID(), null)));
        assertThatThrownBy(() -> service.rotateRefreshToken("old", client)).isInstanceOf(RefreshTokenReuseException.class);
        verify(sessions).revokeFamily(family, RevokeReason.REUSE_DETECTED, now);
    }

    @Test void loggedOutOrExpiredTokensAreRejectedWithoutTheftResponse() {
        stub("logged-out", session(oldId, now.minusSeconds(1), RevokeReason.LOGOUT, null, null));
        assertThatThrownBy(() -> service.rotateRefreshToken("logged-out", client))
                .isInstanceOf(AppException.class).isNotInstanceOf(RefreshTokenReuseException.class);
        stub("expired", new Session(oldId, userId, family, now, null, null, null, null));
        assertThatThrownBy(() -> service.rotateRefreshToken("expired", client)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> service.rotateRefreshToken("unknown", client)).isInstanceOf(AppException.class);
        verify(sessions, never()).revokeFamily(any(), any(), any());
        verify(sessions, never()).create(any(), any(), any(), any(), anyBoolean(), any());
    }

    @Test void logoutRevokesTheDeviceFamilyAndIgnoresUnknownTokens() {
        stub("current", session(oldId, null, null, null, null));
        service.revokeByToken("current");
        service.revokeByToken("unknown");
        verify(sessions).revokeFamily(family, RevokeReason.LOGOUT, now);
        verify(sessions, times(1)).revokeFamily(any(), any(), any());
    }

    @Test void sealedSuccessorOpensOnlyWithThePresentedTokenAndSession() {
        byte[] sealed = SuccessorTokenCipher.seal("old", oldId, "successor-raw");
        assertThatThrownBy(() -> SuccessorTokenCipher.open("other", oldId, sealed)).isInstanceOf(GeneralSecurityException.class);
        assertThatThrownBy(() -> SuccessorTokenCipher.open("old", UUID.randomUUID(), sealed)).isInstanceOf(GeneralSecurityException.class);
    }

    private Session session(UUID id, Instant revokedAt, RevokeReason reason, UUID replacement, byte[] sealed) {
        return new Session(id, userId, family, now.plus(Duration.ofDays(1)), revokedAt, reason, replacement, sealed);
    }
    private void stub(String raw, Session session) {
        when(sessions.find(TokenHash.sha256(raw), false)).thenReturn(Optional.of(session));
        when(sessions.find(TokenHash.sha256(raw), true)).thenReturn(Optional.of(session));
    }
}
