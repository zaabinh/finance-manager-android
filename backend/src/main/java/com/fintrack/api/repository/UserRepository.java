package com.fintrack.api.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.fintrack.api.entity.UserPreferences;
import com.fintrack.api.entity.enums.UserRole;
import com.fintrack.api.entity.enums.UserStatus;
import com.fintrack.api.entity.User;

import org.springframework.context.annotation.Profile;

// One repository for both users and user preferences
@Repository
@Profile("!test")
public class UserRepository {
    private final JdbcTemplate jdbc;
    private static final String SELECT = "SELECT u.*, p.display_name FROM fintrack.users u LEFT JOIN fintrack.user_preferences p ON p.user_id = u.id";
    private static final String SELECT_AUTH = "SELECT u.*, NULL::text AS display_name FROM fintrack.users u";
    private static final RowMapper<UserRecord> MAPPER = (rs, row) -> 
    new UserRecord(
            rs.getObject("id", UUID.class), 
            rs.getString("username"), 
            rs.getString("email"),
            rs.getString("password_hash"), 
            toUserStatus(rs.getString("status")), 
            toUserRole(rs.getString("role")),
            rs.getString("display_name"), 
            rs.getBoolean("onboarding_completed"), 
            rs.getBoolean("email_verified"),
            rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
            rs.getObject("updated_at", java.time.OffsetDateTime.class).toInstant()
        );
    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public UUID create(String username, String email, String passwordHash) {
        UUID id = jdbc.queryForObject("INSERT INTO fintrack.users(username,email,password_hash) VALUES (?,?,?) RETURNING id",
                UUID.class, username, email, passwordHash);
        jdbc.update("INSERT INTO fintrack.user_preferences(user_id) VALUES (?)", id);
        return id;
    }
    public Optional<UserRecord> findByIdentifier(String identifier) {
        return jdbc.query(SELECT_AUTH + " WHERE u.username = ? OR u.email = ?", MAPPER, identifier, identifier).stream().findFirst();
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
    public Optional<UserRecord> lockByIdForAuthentication(UUID id) {
        return jdbc.query(SELECT_AUTH + " WHERE u.id = ? FOR UPDATE OF u", MAPPER, id).stream().findFirst();
    }
    public boolean emailExists(String email) { return findByEmail(email).isPresent(); }
    public boolean usernameExists(String username) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM fintrack.users WHERE username = ?)", Boolean.class, username));
    }
    public void verify(UUID id) { jdbc.update("UPDATE fintrack.users SET email_verified = true, updated_at = now() WHERE id = ?", id); }
    public void updatePassword(UUID id, String hash) {
        jdbc.update("UPDATE fintrack.users SET password_hash = ?, updated_at = now() WHERE id = ?", hash, id);
    }
    public void completeOnboarding(UUID id) {
        jdbc.update("UPDATE fintrack.users SET onboarding_completed = true, updated_at = now() WHERE id = ?", id);
    }
    public void disable(UUID id) {
        jdbc.update("UPDATE fintrack.users SET status = 'DISABLED', updated_at = now() WHERE id = ?", id);
    }
    // 1. Tìm user kèm preferences theo ID
    public Optional<UserRecord> findWithPreferencesById(UUID id) {
        return jdbc.query(SELECT + " WHERE u.id = ?", MAPPER, id).stream().findFirst();
    }

    // 2. Kiểm tra trùng email (bỏ qua user hiện tại, không phân biệt hoa thường)
    public boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id) {
        String sql = "SELECT EXISTS(SELECT 1 FROM fintrack.users WHERE lower(email) = lower(?) AND id != ?)";
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, email, id));
    }

    // 3. Kiểm tra trùng username (bỏ qua user hiện tại, không phân biệt hoa thường)
    public boolean existsByUsernameIgnoreCaseAndIdNot(String username, UUID id) {
        String sql = "SELECT EXISTS(SELECT 1 FROM fintrack.users WHERE lower(username) = lower(?) AND id != ?)";
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, username, id));
    }

    public record UserRecord(
        UUID id, 
        String username, 
        String email, 
        String passwordHash, 
        UserStatus status,
        UserRole role, 
        String displayName, 
        boolean onboardingCompleted, 
        boolean emailVerified,
        java.time.Instant createdAt,
        java.time.Instant updatedAt
) {
    public UserRecord(UUID id, String username, String email, String passwordHash, UserStatus status,
            UserRole role, String displayName, boolean onboardingCompleted, boolean emailVerified) {
        this(id, username, email, passwordHash, status, role, displayName, onboardingCompleted,
                emailVerified, null, null);
    }
    public User toUser() {
        User user = User.builder()
                .id(this.id)
                .username(this.username)
                .email(this.email)
                .passwordHash(this.passwordHash)
                .status(this.status)
                .role(this.role)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt)
                .build();

        if (this.displayName != null) {
            UserPreferences prefs = UserPreferences.builder()
                    .user(user)
                    .id(this.id)
                    .displayName(this.displayName)
                    .build();
            user.setPreferences(prefs);
        }

        return user;
    }
}
    
    private static UserStatus toUserStatus(String status) {
    if (status == null || status.isBlank()) {
        return UserStatus.ACTIVE; // Giá trị mặc định
    }
    try {
        return UserStatus.valueOf(status.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
        return UserStatus.ACTIVE;
    }
}

    private static UserRole toUserRole(String role) {
        if (role == null || role.isBlank()) {
            return UserRole.USER; // Giá trị mặc định
        }
        try {
            return UserRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UserRole.USER;
        }
    }

}
