package com.fintrack.api.service.auth;

import com.fintrack.api.config.AuthProperties;
import com.fintrack.api.dto.response.auth.TokenResponse;
import com.fintrack.api.entity.enums.UserRole;
import com.fintrack.api.entity.enums.UserStatus;
import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.exception.RefreshTokenReuseException;
import com.fintrack.api.repository.RefreshSessionRepository;
import com.fintrack.api.repository.RefreshSessionRepository.RevokeReason;
import com.fintrack.api.repository.RefreshSessionRepository.Session;
import com.fintrack.api.repository.UserRepository;
import com.fintrack.api.repository.UserRepository.UserRecord;

import lombok.RequiredArgsConstructor;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh Token Rotation with reuse detection.
 * <ul>
 *   <li>Every refresh revokes the presented token and issues a successor in the same family (one login/device).</li>
 *   <li>Presenting a rotated token again means two parties hold it: the whole family is revoked (theft response).</li>
 *   <li>Exception: within {@code app.auth.refresh-reuse-grace-period} of the rotation, while the successor is still
 *       unused, the same successor is returned. This absorbs concurrent refreshes and lost responses.</li>
 * </ul>
 * Lock order everywhere: user row, then session row. The user lock serialises all session changes of one user.
 */
@Service
@Profile("!test")
@RequiredArgsConstructor 
public class RefreshTokenRotatingService {
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenRotatingService.class);
    private static final Logger securityLog = LoggerFactory.getLogger("com.fintrack.api.security.audit");
    private static final int REFRESH_TOKEN_BYTES = 32;
    /** Lifetime of a login without "Ghi nhớ tôi" (never longer than the configured refresh TTL). */
    public static final Duration UNREMEMBERED_TTL = Duration.ofDays(1);
    private final RefreshSessionRepository sessions;
    private final UserRepository users;
    private final TokenService tokens;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** Issues an access token and a new refresh token. {@code familyId == null} starts a new family (new login). */
    @Transactional
    public TokenResponse generateTokenPair(
        UUID userId,
        UserRole role,
        UUID familyId,
        ClientMetadata client) {
        return generateTokenPair(userId, role, familyId, client, true);
    }

    /** {@code rememberMe == false} ("Ghi nhớ tôi" unchecked) issues a family that lives at most {@link #UNREMEMBERED_TTL}. */
    @Transactional
    public TokenResponse generateTokenPair(UUID userId, UserRole role, UUID familyId, ClientMetadata client, boolean rememberMe) {
        return issue(userId, role, familyId == null ? UUID.randomUUID() : familyId, client, rememberMe).token();
    }

    @Transactional(noRollbackFor = RefreshTokenReuseException.class)
    public TokenResponse rotateRefreshToken(String raw, ClientMetadata client) {
        String hash = TokenHash.sha256(raw);
        var candidate = sessions.find(hash, false).orElseThrow(RefreshTokenRotatingService::invalid);
        UserRecord user = users.lockById(candidate.userId()).orElseThrow(RefreshTokenRotatingService::invalid);
        Session session = sessions.find(hash, true).orElseThrow(RefreshTokenRotatingService::invalid);
        Instant now = clock.instant();

        if (session.revoked()) {
            if (session.revokeReason() != RevokeReason.ROTATED || !isUsable(user)) throw invalid(); // logged out, ...
            TokenResponse graceReply = graceReply(raw, session, user, now);
            if (graceReply != null) return graceReply;
            int revoked = sessions.revokeFamily(session.familyId(), RevokeReason.REUSE_DETECTED, now);
            securityLog.error("SECURITY refresh token reuse detected; family revoked: userId={} familyId={} sessionId={} "
                    + "rotatedAt={} revokedSessions={} ip={} userAgent={}", user.id(), session.familyId(), session.id(),
                    session.revokedAt(), revoked, sanitize(client.ipAddress()), sanitize(client.userAgent()));
            throw new RefreshTokenReuseException();
        }
        if (!session.expiresAt().isAfter(now) || !isUsable(user)) throw invalid();

        // Carry the flag forward so refreshing cannot turn a short session into a 30-day one.
        Issued next = issue(user.id(), user.role(), session.familyId(), client, session.rememberMe());
        sessions.rotate(session.id(), next.sessionId(), SuccessorTokenCipher.seal(raw, session.id(), next.refreshToken()), now);
        log.debug("Refresh token rotated; familyId={} from={} to={}", session.familyId(), session.id(), next.sessionId());
        return next.token();
    }

    /** Logout: revokes the device chain of the presented token. Unknown tokens are ignored (idempotent). */
    @Transactional
    public void revokeByToken(String raw) {
        String hash = TokenHash.sha256(raw);
        sessions.find(hash, false).ifPresent(candidate -> {
            users.lockById(candidate.userId());
            revokeFamily(candidate.familyId(), RevokeReason.LOGOUT);
        });
    }

    /** Revokes one device chain. Callers must already hold the user lock (see class comment). */
    @Transactional
    public void revokeFamily(UUID familyId, RevokeReason reason) {
        int revoked = sessions.revokeFamily(familyId, reason, clock.instant());
        log.info("Refresh token family revoked; familyId={} reason={} sessions={}", familyId, reason, revoked);
    }

    /** Signs the user out everywhere. Callers must already hold the user lock (see class comment). */
    @Transactional
    public void revokeAllUserSessions(UUID userId, RevokeReason reason) {
        int revoked = sessions.revokeAll(userId, reason, clock.instant());
        log.info("All refresh sessions revoked; userId={} reason={} sessions={}", userId, reason, revoked);
    }

    /** Returns the already issued successor if the race window applies, otherwise {@code null}. */
    private TokenResponse graceReply(String raw, Session session, UserRecord user, Instant now) {
        if (session.replacementId() == null || session.sealedSuccessor() == null
                || now.isAfter(session.revokedAt().plus(properties.refreshReuseGracePeriod()))) return null;
        var successor = sessions.findById(session.replacementId()).orElse(null);
        if (successor == null || !successor.activeAt(now)) return null; // successor already used: real reuse
        try {
            String successorRefresh = SuccessorTokenCipher.open(raw, session.id(), session.sealedSuccessor());
            log.info("Refresh token replayed inside grace period; returning existing successor; familyId={} sessionId={}",
                    session.familyId(), session.id());
            return pair(tokens.createAccessToken(user.id(), user.role()), successorRefresh);
        } catch (GeneralSecurityException exception) {
            return null;
        }
    }

    private Issued issue(
        UUID userId, 
        UserRole role, 
        UUID familyId,
        ClientMetadata client,
        boolean rememberMe) {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        // 256-bit random tokens need no salt or slow hash: SHA-256 is preimage-resistant and allows indexed lookup.
        Duration ttl = rememberMe ? properties.refreshTokenTtl()
                : java.util.Collections.min(java.util.List.of(properties.refreshTokenTtl(), UNREMEMBERED_TTL));
        UUID id = sessions.create(userId, familyId, TokenHash.sha256(refresh), clock.instant().plus(ttl), rememberMe, client);
        return new Issued(id, refresh, pair(tokens.createAccessToken(userId, role), refresh));
    }

    private TokenResponse pair(String accessToken, String refreshToken) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", properties.accessTokenTtl().toSeconds());
    }

    private static boolean isUsable(UserRecord user) { return user.status() == UserStatus.ACTIVE && user.emailVerified(); }

    // Client-supplied values must not be able to forge log lines.
    private static String sanitize(String value) { return value == null ? null : value.replaceAll("[\\r\\n\\t]", "_"); }

    private static AppException invalid() {
        return new AppException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_REFRESH_TOKEN_INVALID, "Invalid refresh token");
    }

    private record Issued(UUID sessionId, String refreshToken, TokenResponse token) {
        @Override public String toString() { return "Issued[sessionId=" + sessionId + "]"; }
    }
}
