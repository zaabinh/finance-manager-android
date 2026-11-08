package com.fintrack.api.mapper;

import com.fintrack.api.dto.response.user.NotificationSettingsResponse;
import com.fintrack.api.dto.response.user.UserPreferencesResponse;
import com.fintrack.api.dto.response.user.UserProfileResponse;
import com.fintrack.api.entity.NotificationSettings;
import com.fintrack.api.entity.User;
import com.fintrack.api.entity.UserPreferences;
import com.fintrack.api.repository.UserPreferencesRepository.UserPreferencesRecord;
import com.fintrack.api.repository.UserRepository.UserRecord;

import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public User toEntity(UserRecord record) {
        if (record == null) {
            return null;
        }
        // 1. Tạo đối tượng User
        User user = User.builder()
                .id(record.id())
                .username(record.username())
                .email(record.email())
                .passwordHash(record.passwordHash())
                .role(record.role())
                .status(record.status())
                .build();

        // 2. Khởi tạo UserPreferences và gắn liên kết hai chiều nếu có displayName
        if (record.displayName() != null) {
            UserPreferences preferences = UserPreferences.builder()
                    .user(user)
                    .id(record.id())
                    .displayName(record.displayName())
                    .currencyCode("VND")
                    .timezone("Asia/Ho_Chi_Minh")
                    .weekStartsOn((short) 1)
                    .build();

            user.setPreferences(preferences);
        }
        return user;
    }

    public UserPreferences toEntity(UserPreferencesRecord record, User user) {
        if (record == null) {
            return null;
        }
        if (user == null || !record.userId().equals(user.getId())) {
            throw new IllegalArgumentException("User and user preferences are not match");
        }

        return UserPreferences.builder()
                .user(user)
                .displayName(record.displayName())
                .currencyCode(record.currencyCode())
                .timezone(record.timeZone())
                .weekStartsOn(record.weekStartsOn())
                .updatedAt(record.updatedAt())
                .build();
    }

    public UserProfileResponse toProfileResponse(User user, UserPreferences prefs) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                prefs != null ? prefs.getDisplayName() : null,
                user.getRole(),
                user.getStatus(),
                prefs != null ? prefs.getCurrencyCode() : "VND",
                prefs != null ? prefs.getTimezone() : "Asia/Ho_Chi_Minh",
                prefs != null ? prefs.getWeekStartsOn() : (short) 1,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    public UserPreferencesResponse toPreferencesResponse(UserPreferences prefs) {
        return new UserPreferencesResponse(
                prefs.getId(),
                prefs.getDisplayName(),
                prefs.getCurrencyCode(),
                prefs.getTimezone(),
                prefs.getWeekStartsOn(),
                prefs.getUpdatedAt()
        );
    }

    public NotificationSettingsResponse toNotificationSettingsResponse(NotificationSettings settings) {
        return new NotificationSettingsResponse(
                settings.getNotificationsEnabled(),
                settings.getDailyReminderEnabled(),
                settings.getDailyReminderTime(),
                settings.getBudgetAlertEnabled(),
                settings.getBudgetAlertThresholdPercent(),
                settings.getBudgetExceedAlertEnabled(),
                settings.getOtherNotificationsEnabled(),
                settings.getUpdatedAt()
        );
    }
}
