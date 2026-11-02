package com.fintrack.api.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!test")
public class VerificationChallengeRepository {
    public enum Purpose { EMAIL_VERIFICATION, PASSWORD_RESET }
    private final JdbcTemplate jdbc;
    public VerificationChallengeRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void invalidate(UUID userId, Purpose purpose, Instant now) {
        jdbc.update("UPDATE fintrack.verification_challenges SET consumed_at = ? WHERE user_id = ? AND purpose = ? AND consumed_at IS NULL",
                Timestamp.from(now), userId, purpose.name());
    }
    public void create(UUID userId, String email, Purpose purpose, String hash, Instant now, Instant expiry) {
        jdbc.update("""
                INSERT INTO fintrack.verification_challenges(user_id,email,purpose,code_hash,created_at,expires_at)
                VALUES (?,?,?,?,?,?)
                """, userId, email, purpose.name(), hash, Timestamp.from(now), Timestamp.from(expiry));
    }
    public Optional<Challenge> lockLatest(UUID userId, Purpose purpose) {
        return jdbc.query("""
                SELECT * FROM fintrack.verification_challenges WHERE user_id = ? AND purpose = ? AND consumed_at IS NULL
                ORDER BY created_at DESC LIMIT 1 FOR UPDATE
                """, (rs, row) -> new Challenge(rs.getObject("id", UUID.class), rs.getString("code_hash").trim(),
                rs.getTimestamp("expires_at").toInstant(), rs.getInt("attempt_count")), userId, purpose.name()).stream().findFirst();
    }
    public void fail(UUID id) { jdbc.update("UPDATE fintrack.verification_challenges SET attempt_count = attempt_count + 1 WHERE id = ? AND attempt_count < 5", id); }
    public void consume(UUID id, Instant now) { jdbc.update("UPDATE fintrack.verification_challenges SET consumed_at = ? WHERE id = ?", Timestamp.from(now), id); }
    public record Challenge(UUID id, String codeHash, Instant expiresAt, int attempts) { }
}
