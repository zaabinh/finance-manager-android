package com.fintrack.api.service.notification;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.repository.NotificationSettingsRepository;
import com.fintrack.api.service.finance.*;
import java.sql.Time;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test")
public class NotificationSettingsV2Service {
    public static class Patch extends FinanceRequest {
        @JsonDeserialize(using=FinanceBooleanDeserializer.class) public Boolean notificationsEnabled;
        @JsonDeserialize(using=FinanceBooleanDeserializer.class) public Boolean dailyReminderEnabled;
        public LocalTime dailyReminderTime;
        @JsonDeserialize(using=FinanceBooleanDeserializer.class) public Boolean budgetAlertEnabled;
        @JsonDeserialize(using=FinanceIntegerDeserializer.class) public Integer budgetAlertThresholdPercent;
        @JsonDeserialize(using=FinanceBooleanDeserializer.class) public Boolean budgetExceedAlertEnabled;
        @JsonDeserialize(using=FinanceBooleanDeserializer.class) public Boolean otherNotificationsEnabled;
        @JsonDeserialize(using=FinanceBooleanDeserializer.class) public Boolean emailFinanceEnabled;
    }
    public record Settings(boolean notificationsEnabled,boolean dailyReminderEnabled,LocalTime dailyReminderTime,
            boolean budgetAlertEnabled,int budgetAlertThresholdPercent,boolean budgetExceedAlertEnabled,
            boolean otherNotificationsEnabled,boolean emailFinanceEnabled,Instant updatedAt) {}
    private final JdbcTemplate jdbc;
    private final NotificationSettingsRepository settings;
    public NotificationSettingsV2Service(JdbcTemplate jdbc,NotificationSettingsRepository settings) { this.jdbc=jdbc;this.settings=settings; }
    @Transactional public Settings get(UUID user) { active(user); settings.createDefault(user); return read(user); }
    @Transactional public Settings patch(UUID user,Patch p) {
        if(p==null||(p.notificationsEnabled==null&&p.dailyReminderEnabled==null&&p.dailyReminderTime==null&&p.budgetAlertEnabled==null&&p.budgetAlertThresholdPercent==null&&p.budgetExceedAlertEnabled==null&&p.otherNotificationsEnabled==null&&p.emailFinanceEnabled==null))
            throw FinanceSupport.badRequest("At least one notification setting is required");
        boolean verified=active(user); settings.createDefault(user); settings.lockByUserId(user);
        if(Boolean.TRUE.equals(p.emailFinanceEnabled)&&!verified) throw new AppException(HttpStatus.CONFLICT,ErrorCode.NOTIFICATION_EMAIL_UNVERIFIED,"Verify your account email before enabling finance email");
        if(p.budgetAlertThresholdPercent!=null&&(p.budgetAlertThresholdPercent<1||p.budgetAlertThresholdPercent>100)) throw FinanceSupport.badRequest("Invalid budget threshold");
        if(p.dailyReminderTime!=null&&(p.dailyReminderTime.getSecond()!=0||p.dailyReminderTime.getNano()!=0)) throw FinanceSupport.badRequest("Reminder time must have minute precision");
        jdbc.update("""
            UPDATE fintrack.notification_settings SET
            notifications_enabled=COALESCE(?,notifications_enabled),daily_reminder_enabled=COALESCE(?,daily_reminder_enabled),
            daily_reminder_time=COALESCE(?,daily_reminder_time,'20:00'::time),budget_alert_enabled=COALESCE(?,budget_alert_enabled),
            budget_alert_threshold_percent=COALESCE(?,budget_alert_threshold_percent),budget_exceed_alert_enabled=COALESCE(?,budget_exceed_alert_enabled),
            other_notifications_enabled=COALESCE(?,other_notifications_enabled),email_finance_enabled=COALESCE(?,email_finance_enabled)
            WHERE user_id=?
            """,p.notificationsEnabled,p.dailyReminderEnabled,p.dailyReminderTime==null?null:Time.valueOf(p.dailyReminderTime),p.budgetAlertEnabled,p.budgetAlertThresholdPercent,p.budgetExceedAlertEnabled,p.otherNotificationsEnabled,p.emailFinanceEnabled,user);
        return read(user);
    }
    private boolean active(UUID user) {
        return jdbc.query("SELECT status,email_verified FROM fintrack.users WHERE id=?",(rs,row)-> {
            if(!"ACTIVE".equals(rs.getString("status"))) throw new AppException(HttpStatus.FORBIDDEN,ErrorCode.FORBIDDEN,"User account is not active");
            return rs.getBoolean("email_verified");
        },user).stream().findFirst().orElseThrow(FinanceSupport::notFound);
    }
    private Settings read(UUID user) {
        return jdbc.queryForObject("SELECT * FROM fintrack.notification_settings WHERE user_id=?",(rs,row)->new Settings(rs.getBoolean("notifications_enabled"),rs.getBoolean("daily_reminder_enabled"),rs.getTime("daily_reminder_time")==null?null:rs.getTime("daily_reminder_time").toLocalTime(),rs.getBoolean("budget_alert_enabled"),rs.getInt("budget_alert_threshold_percent"),rs.getBoolean("budget_exceed_alert_enabled"),rs.getBoolean("other_notifications_enabled"),rs.getBoolean("email_finance_enabled"),rs.getTimestamp("updated_at").toInstant()),user);
    }
}
