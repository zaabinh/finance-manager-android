package com.fintrack.api.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

// One repository for both users and user preferences
@Repository
@Profile("!test")
public class UserRepository {
    private final JdbcTemplate jdbc;
    private static final String SELECT = "SELECT u.*, p.display_name FROM fintrack.users u LEFT JOIN fintrack.user_preferences p ON p.user_id = u.id";
    private static final RowMapper<UserRecord> MAPPER = (rs, row) -> 
    new UserRecord(
            rs.getObject("id", UUID.class), 
            rs.getString("username"), 
            rs.getString("email"),
            rs.getString("password_hash"), 
            rs.getString("status"), 
            rs.getString("role"),
            rs.getString("display_name"), 
            rs.getBoolean("onboarding_completed"), 
            rs.getBoolean("email_verified")
        );
    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public UUID create(String username, String email, String passwordHash) {
        UUID id = jdbc.queryForObject("INSERT INTO fintrack.users(username,email,password_hash) VALUES (?,?,?) RETURNING id",
                UUID.class, username, email, passwordHash);
        jdbc.update("INSERT INTO fintrack.user_preferences(user_id) VALUES (?)", id);
        return id;
    }
    public Optional<UserRecord> findByIdentifier(String identifier) {
        return jdbc.query(SELECT + " WHERE u.username = ? OR u.email = ?", MAPPER, identifier, identifier).stream().findFirst();
    }
    public Optional<UserRecord> findByEmail(String email) {
        return jdbc.query(SELECT + " WHERE u.email = ?", MAPPER, email).stream().findFirst();
    }
    public Optional<UserRecord> findById(UUID id) {
        return jdbc.query(SELECT + " WHERE u.id = ?", MAPPER, id).stream().findFirst();
    }
    public Optional<UserRecord> lockById(UUID id) {
        return jdbc.query(SELECT + " WHERE u.id = ? FOR UPDATE OF u", MAPPER, id).stream().findFirst();
    }
    public boolean emailExists(String email) { return findByEmail(email).isPresent(); }
    public boolean usernameExists(String username) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM fintrack.users WHERE username = ?)", Boolean.class, username));
    }
    public void verify(UUID id) { jdbc.update("UPDATE fintrack.users SET email_verified = true, updated_at = now() WHERE id = ?", id); }
    public void updatePassword(UUID id, String hash) {
        jdbc.update("UPDATE fintrack.users SET password_hash = ?, updated_at = now() WHERE id = ?", hash, id);
    }
    public record UserRecord(UUID id, String username, String email, String passwordHash, String status,
            String role, String displayName, boolean onboardingCompleted, boolean emailVerified) { }
}
