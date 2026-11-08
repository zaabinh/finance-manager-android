package com.fintrack.api.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("!test")
public class NotificationSettingsRepository {

    private final JdbcTemplate jdbc;

    private static final String SELECT = """
            SELECT user_id, notifications_enabled, daily_reminder_enabled, daily_reminder_time,
                   budget_alert_enabled, budget_alert_threshold_percent,
                   budget_exceed_alert_enabled, other_notifications_enabled, updated_at
            FROM fintrack.notification_settings
            """;

    private static final RowMapper<NotificationSettingsRecord> MAPPER = (rs, rowNum) -> {
        Time sqlTime = rs.getTime("daily_reminder_time");
        LocalTime reminderTime = (sqlTime != null) ? sqlTime.toLocalTime() : null;

        return new NotificationSettingsRecord(
                rs.getObject("user_id", UUID.class),
                rs.getBoolean("notifications_enabled"),
                rs.getBoolean("daily_reminder_enabled"),
                reminderTime,
                rs.getBoolean("budget_alert_enabled"),
                rs.getShort("budget_alert_threshold_percent"),
                rs.getBoolean("budget_exceed_alert_enabled"),
                rs.getBoolean("other_notifications_enabled"),
                rs.getObject("updated_at", java.time.OffsetDateTime.class).toInstant()
        );
    };

    public NotificationSettingsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 1. Tìm thiết lập thông báo theo userId
    public Optional<NotificationSettingsRecord> findByUserId(UUID userId) {
        return jdbc.query(SELECT + " WHERE user_id = ?", MAPPER, userId)
                .stream()
                .findFirst();
    }

    // 2. Khởi tạo thiết lập mặc định (nếu chưa có)
    public void createDefault(UUID userId) {
        String sql = """
                INSERT INTO fintrack.notification_settings(
                    user_id, daily_reminder_enabled, daily_reminder_time, 
                    budget_alert_enabled, budget_alert_threshold_percent
                ) VALUES (?, false, '20:00:00'::time, true, 80)
                ON CONFLICT (user_id) DO NOTHING
                """;
        jdbc.update(sql, userId);
    }

    // 3. Cập nhật hoặc thêm mới (Upsert) thiết lập thông báo
    public void upsert(UUID userId, boolean notificationsEnabled, boolean dailyReminderEnabled,
                       LocalTime dailyReminderTime, boolean budgetAlertEnabled,
                       short budgetAlertThresholdPercent, boolean budgetExceedAlertEnabled,
                       boolean otherNotificationsEnabled) {
        String sql = """
                INSERT INTO fintrack.notification_settings (
                    user_id, notifications_enabled, daily_reminder_enabled, daily_reminder_time,
                    budget_alert_enabled, budget_alert_threshold_percent,
                    budget_exceed_alert_enabled, other_notifications_enabled, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (user_id) DO UPDATE SET
                    notifications_enabled = EXCLUDED.notifications_enabled,
                    daily_reminder_enabled = EXCLUDED.daily_reminder_enabled,
                    daily_reminder_time = EXCLUDED.daily_reminder_time,
                    budget_alert_enabled = EXCLUDED.budget_alert_enabled,
                    budget_alert_threshold_percent = EXCLUDED.budget_alert_threshold_percent,
                    budget_exceed_alert_enabled = EXCLUDED.budget_exceed_alert_enabled,
                    other_notifications_enabled = EXCLUDED.other_notifications_enabled,
                    updated_at = now()
                """;

        Time sqlTime = (dailyReminderTime != null) ? Time.valueOf(dailyReminderTime) : null;
        jdbc.update(sql, userId, notificationsEnabled, dailyReminderEnabled, sqlTime,
                budgetAlertEnabled, budgetAlertThresholdPercent, budgetExceedAlertEnabled,
                otherNotificationsEnabled);
    }

    // 4. Record bất biến đại diện cho 1 dòng trong bảng fintrack.notification_settings
    public record NotificationSettingsRecord(
            UUID userId,
            boolean notificationsEnabled,
            boolean dailyReminderEnabled,
            LocalTime dailyReminderTime,
            boolean budgetAlertEnabled,
            short budgetAlertThresholdPercent,
            boolean budgetExceedAlertEnabled,
            boolean otherNotificationsEnabled,
            Instant updatedAt
    ) {}
}
