package com.fintrack.api.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

@Repository
@Profile("!test")
public class UserRepository {
    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void create(String username, String email, String passwordHash) {
        UUID id = jdbc.queryForObject("""
                INSERT INTO fintrack.users(username, email, password_hash)
                VALUES (?, ?, ?) RETURNING id
                """, UUID.class, username, email, passwordHash);
        jdbc.update("INSERT INTO fintrack.user_preferences(user_id) VALUES (?)", id);
    }

    public Optional<UserRecord> findByIdentifier(String identifier) {
        return jdbc.query("""
                SELECT users.id, users.username, users.password_hash, users.status, users.role,
                       preferences.display_name, users.onboarding_completed
                FROM fintrack.users users
                LEFT JOIN fintrack.user_preferences preferences ON preferences.user_id = users.id
                WHERE users.username = ? OR users.email = ?
                """, result -> result.next() ? Optional.of(new UserRecord(
                result.getObject("id", UUID.class), result.getString("username"),
                result.getString("password_hash"), result.getString("status"), result.getString("role"),
                result.getString("display_name"), result.getBoolean("onboarding_completed"))) : Optional.empty(),
                identifier, identifier);
    }

    public record UserRecord(UUID id, String username, String passwordHash, String status, String role,
            String displayName, boolean onboardingCompleted) { }
}
