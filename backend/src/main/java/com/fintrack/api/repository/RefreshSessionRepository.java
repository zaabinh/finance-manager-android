package com.fintrack.api.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.fintrack.api.service.auth.ClientMetadata;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
public class RefreshSessionRepository {
    public enum RevokeReason { ROTATED, LOGOUT, REUSE_DETECTED, PASSWORD_CHANGED, PASSWORD_RESET }
    private static final RowMapper<Session> MAPPER = (rs, row) -> {
        Timestamp revokedAt = rs.getTimestamp("revoked_at");
        String reason = rs.getString("revoke_reason");
        return new Session(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getObject("family_id", UUID.class), rs.getTimestamp("expires_at").toInstant(),
                revokedAt == null ? null : revokedAt.toInstant(), reason == null ? null : RevokeReason.valueOf(reason),
                rs.getObject("replaced_by_session_id", UUID.class), rs.getBytes("successor_token_ciphertext"),
                rs.getBoolean("remember_me"));
    };
    private final JdbcTemplate jdbc;
    public RefreshSessionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public UUID create(UUID userId, UUID familyId, String tokenHash, Instant expiresAt, boolean rememberMe, ClientMetadata client) {
        return jdbc.queryForObject("""
                INSERT INTO fintrack.refresh_sessions(user_id,family_id,token_hash,expires_at,remember_me,user_agent,ip_address)
                VALUES (?,?,?,?,?,?,CAST(? AS inet)) RETURNING id
                """, UUID.class, userId, familyId, tokenHash, Timestamp.from(expiresAt), rememberMe, client.userAgent(), client.ipAddress());
    }
    public Optional<Session> find(String hash, boolean lock) {
        return jdbc.query("SELECT * FROM fintrack.refresh_sessions WHERE token_hash = ?" + (lock ? " FOR UPDATE" : ""),
                MAPPER, hash).stream().findFirst();
    }
    public Optional<Session> findById(UUID id) {
        return jdbc.query("SELECT * FROM fintrack.refresh_sessions WHERE id = ?", MAPPER, id).stream().findFirst();
    }
    /** Marks {@code id} as rotated into {@code replacement} and keeps the sealed successor for the grace period. */
    public void rotate(UUID id, UUID replacement, byte[] sealedSuccessor, Instant now) {
        jdbc.update("""
                UPDATE fintrack.refresh_sessions SET revoked_at = ?, revoke_reason = 'ROTATED', last_used_at = ?,
                       replaced_by_session_id = ?, successor_token_ciphertext = ?
                 WHERE id = ?
                """, Timestamp.from(now), Timestamp.from(now), replacement, sealedSuccessor, id);
        // The predecessor's grace window is over once its successor is itself rotated.
        jdbc.update("UPDATE fintrack.refresh_sessions SET successor_token_ciphertext = NULL WHERE replaced_by_session_id = ?", id);
    }
    public int revokeFamily(UUID familyId, RevokeReason reason, Instant now) {
        return jdbc.update("""
                UPDATE fintrack.refresh_sessions
                   SET revoked_at = COALESCE(revoked_at, ?), revoke_reason = COALESCE(revoke_reason, ?),
                       successor_token_ciphertext = NULL
                 WHERE family_id = ? AND (revoked_at IS NULL OR successor_token_ciphertext IS NOT NULL)
                """, Timestamp.from(now), reason.name(), familyId);
    }
    public int revokeAll(UUID userId, RevokeReason reason, Instant now) {
        return jdbc.update("""
                UPDATE fintrack.refresh_sessions
                   SET revoked_at = COALESCE(revoked_at, ?), revoke_reason = COALESCE(revoke_reason, ?),
                       successor_token_ciphertext = NULL
                 WHERE user_id = ? AND (revoked_at IS NULL OR successor_token_ciphertext IS NOT NULL)
                """, Timestamp.from(now), reason.name(), userId);
    }
    public record Session(UUID id, UUID userId, UUID familyId, Instant expiresAt, Instant revokedAt,
            RevokeReason revokeReason, UUID replacementId, byte[] sealedSuccessor, boolean rememberMe) {
        public Session(UUID id, UUID userId, UUID familyId, Instant expiresAt, Instant revokedAt,
                RevokeReason revokeReason, UUID replacementId, byte[] sealedSuccessor) {
            this(id, userId, familyId, expiresAt, revokedAt, revokeReason, replacementId, sealedSuccessor, true);
        }
        public boolean revoked() { return revokedAt != null; }
        public boolean activeAt(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
        @Override public String toString() { return "Session[id=" + id + ", familyId=" + familyId + "]"; }
    }
}
