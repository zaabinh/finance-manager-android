package com.fintrack.api.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!test")
public class UserPreferencesRepository {

    private final JdbcTemplate jdbc;

    private static final String SELECT = """
            SELECT user_id, display_name, currency_code, time_zone, 
                   week_starts_on, notifications_enabled, updated_at 
            FROM fintrack.user_preferences
            """;

    private static final RowMapper<UserPreferencesRecord> MAPPER = (rs, rowNum) ->
            new UserPreferencesRecord(
                    rs.getObject("user_id", UUID.class),
                    rs.getString("display_name"),
                    rs.getString("currency_code"),
                    rs.getString("time_zone"),
                    rs.getShort("week_starts_on"),
                    rs.getBoolean("notifications_enabled"),
                    rs.getObject("updated_at", java.time.OffsetDateTime.class).toInstant()
            );

    public UserPreferencesRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 1. Tìm cài đặt tùy chọn theo userId
    public Optional<UserPreferencesRecord> findByUserId(UUID userId) {
        return jdbc.query(SELECT + " WHERE user_id = ?", MAPPER, userId)
                .stream()
                .findFirst();
    }

    // 2. Khởi tạo bản ghi mặc định cho user mới
    public void createDefault(UUID userId, String displayName) {
        String sql = """
                INSERT INTO fintrack.user_preferences(user_id, display_name) 
                VALUES (?, ?) 
                ON CONFLICT (user_id) DO NOTHING
                """;
        jdbc.update(sql, userId, displayName);
    }

    // 3. Cập nhật hoặc thêm mới (Upsert) cấu hình tùy chọn
    public void upsert(UUID userId, String displayName, String currencyCode, 
                       String timeZone, short weekStartsOn, boolean notificationsEnabled) {
        String sql = """
                INSERT INTO fintrack.user_preferences (
                    user_id, display_name, currency_code, time_zone, 
                    week_starts_on, notifications_enabled, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (user_id) DO UPDATE SET
                    display_name = EXCLUDED.display_name,
                    currency_code = EXCLUDED.currency_code,
                    time_zone = EXCLUDED.time_zone,
                    week_starts_on = EXCLUDED.week_starts_on,
                    notifications_enabled = EXCLUDED.notifications_enabled,
                    updated_at = now()
                """;
        jdbc.update(sql, userId, displayName, currencyCode, timeZone, weekStartsOn, notificationsEnabled);
    }

    // 4. Record bất biến đại diện cho 1 dòng trong bảng fintrack.user_preferences
    public record UserPreferencesRecord(
            UUID userId,
            String displayName,
            String currencyCode,
            String timeZone,
            short weekStartsOn,
            boolean notificationsEnabled,
            Instant updatedAt
    ) {}
}