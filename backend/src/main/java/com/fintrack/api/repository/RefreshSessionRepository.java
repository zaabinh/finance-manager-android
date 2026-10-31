package com.fintrack.api.repository;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

@Repository
@Profile("!test")
public class RefreshSessionRepository {
    private final JdbcTemplate jdbc;

    public RefreshSessionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void create(UUID userId, String tokenHash, java.time.Instant expiresAt) {
        jdbc.update("""
                INSERT INTO fintrack.refresh_sessions(user_id, token_hash, expires_at)
                VALUES (?, ?, ?)
                """, userId, tokenHash, java.sql.Timestamp.from(expiresAt));
    }
}
