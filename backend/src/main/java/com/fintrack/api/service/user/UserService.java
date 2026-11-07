package com.fintrack.api.service.user;

import com.fintrack.api.dto.request.user.DeactivateUserRequest;
import com.fintrack.api.dto.request.user.UpdateNotificationSettingsRequest;
import com.fintrack.api.dto.request.user.UpdatePreferencesRequest;
import com.fintrack.api.dto.request.user.UpdateProfileRequest;
import com.fintrack.api.dto.response.user.NotificationSettingsResponse;
import com.fintrack.api.dto.response.user.UserPreferencesResponse;
import com.fintrack.api.dto.response.user.UserProfileResponse;
import com.fintrack.api.dto.response.user.UserSettingsResponse;
import com.fintrack.api.entity.enums.UserStatus;
import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.repository.NotificationSettingsRepository;
import com.fintrack.api.repository.NotificationSettingsRepository.NotificationSettingsRecord;
import com.fintrack.api.repository.RefreshSessionRepository.RevokeReason;
import com.fintrack.api.repository.UserPreferencesRepository;
import com.fintrack.api.repository.UserPreferencesRepository.UserPreferencesRecord;
import com.fintrack.api.repository.UserRepository;
import com.fintrack.api.repository.UserRepository.UserRecord;
import com.fintrack.api.service.auth.RefreshTokenRotatingService;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test")
@RequiredArgsConstructor
public class UserService {
    private final UserRepository users;
    private final UserPreferencesRepository preferences;
    private final NotificationSettingsRepository notificationSettings;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRotatingService refreshTokens;

    @Transactional
    public UserProfileResponse getMyProfile(UUID userId) {
        UserRecord user = getActiveUser(userId);
        return toProfile(user, getOrCreatePreferences(user));
    }

    @Transactional
    public UserSettingsResponse getMySettings(UUID userId) {
        UserRecord user = getActiveUser(userId);
        UserPreferencesRecord prefs = getOrCreatePreferences(user);
        NotificationSettingsRecord notifications = getOrCreateNotificationSettings(user.id());
        return new UserSettingsResponse(toProfile(user, prefs), toPreferences(prefs), toNotifications(notifications));
    }

    @Transactional
    public UserProfileResponse updateMyProfile(UUID userId, UpdateProfileRequest request) {
        if (request.displayName() == null && request.timeZone() == null) {
            throw badRequest("At least one profile field is required");
        }
        UserRecord user = getActiveUser(userId);
        UserPreferencesRecord current = getOrCreatePreferences(user);
        String displayName = request.displayName() == null ? current.displayName() : normalizedDisplayName(request.displayName());
        String timeZone = request.timeZone() == null ? current.timeZone() : validTimeZone(request.timeZone());
        preferences.upsert(userId, displayName, current.currencyCode(), timeZone, current.weekStartsOn());
        return toProfile(user, requirePreferences(userId));
    }

    @Transactional
    public UserPreferencesResponse getMyPreferences(UUID userId) {
        return toPreferences(getOrCreatePreferences(getActiveUser(userId)));
    }

    @Transactional
    public UserPreferencesResponse updateMyPreferences(UUID userId, UpdatePreferencesRequest request) {
        if (request.timeZone() == null) throw badRequest("timeZone is required");
        UserRecord user = getActiveUser(userId);
        UserPreferencesRecord current = getOrCreatePreferences(user);
        preferences.upsert(userId, current.displayName(), current.currencyCode(),
                validTimeZone(request.timeZone()), current.weekStartsOn());
        return toPreferences(requirePreferences(userId));
    }

    @Transactional
    public NotificationSettingsResponse getMyNotificationSettings(UUID userId) {
        getActiveUser(userId);
        return toNotifications(getOrCreateNotificationSettings(userId));
    }

    @Transactional
    public NotificationSettingsResponse updateMyNotificationSettings(
            UUID userId, UpdateNotificationSettingsRequest request) {
        if (request.notificationsEnabled() == null && request.dailyReminderEnabled() == null
                && request.dailyReminderTime() == null && request.budgetAlertEnabled() == null
                && request.budgetAlertThresholdPercent() == null && request.budgetExceedAlertEnabled() == null
                && request.otherNotificationsEnabled() == null) {
            throw badRequest("At least one notification setting is required");
        }
        getActiveUser(userId);
        NotificationSettingsRecord current = getOrCreateNotificationSettings(userId);
        boolean enabled = valueOr(request.notificationsEnabled(), current.notificationsEnabled());
        boolean dailyEnabled = valueOr(request.dailyReminderEnabled(), current.dailyReminderEnabled());
        LocalTime dailyTime = request.dailyReminderTime() == null ? current.dailyReminderTime() : request.dailyReminderTime();
        boolean budgetEnabled = valueOr(request.budgetAlertEnabled(), current.budgetAlertEnabled());
        short threshold = request.budgetAlertThresholdPercent() == null
                ? current.budgetAlertThresholdPercent() : request.budgetAlertThresholdPercent();
        boolean exceedEnabled = valueOr(request.budgetExceedAlertEnabled(), current.budgetExceedAlertEnabled());
        boolean otherEnabled = valueOr(request.otherNotificationsEnabled(), current.otherNotificationsEnabled());
        if (dailyEnabled && dailyTime == null) dailyTime = LocalTime.of(20, 0);
        notificationSettings.upsert(userId, enabled, dailyEnabled, dailyTime, budgetEnabled,
                threshold, exceedEnabled, otherEnabled);
        return toNotifications(requireNotificationSettings(userId));
    }

    @Transactional
    public void deactivateMyAccount(UUID userId, DeactivateUserRequest request) {
        UserRecord user = users.lockById(userId).orElseThrow(UserService::notFound);
        if (user.status() != UserStatus.ACTIVE) throw new AppException(
                HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "User account is not active");
        if (!passwordEncoder.matches(request.currentPassword(), user.passwordHash())) {
            throw badRequest("Current password is incorrect");
        }
        users.disable(userId);
        refreshTokens.revokeAllUserSessions(userId, RevokeReason.LOGOUT);
    }

    private UserRecord getActiveUser(UUID userId) {
        UserRecord user = users.findById(userId).orElseThrow(UserService::notFound);
        if (user.status() != UserStatus.ACTIVE) throw new AppException(
                HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "User account is not active");
        return user;
    }

    private UserPreferencesRecord getOrCreatePreferences(UserRecord user) {
        return preferences.findByUserId(user.id()).orElseGet(() -> {
            preferences.createDefault(user.id(), user.username());
            return requirePreferences(user.id());
        });
    }

    private UserPreferencesRecord requirePreferences(UUID userId) {
        return preferences.findByUserId(userId).orElseThrow(() -> new AppException(
                HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "User preferences are unavailable"));
    }

    private NotificationSettingsRecord getOrCreateNotificationSettings(UUID userId) {
        return notificationSettings.findByUserId(userId).orElseGet(() -> {
            notificationSettings.createDefault(userId);
            return requireNotificationSettings(userId);
        });
    }

    private NotificationSettingsRecord requireNotificationSettings(UUID userId) {
        return notificationSettings.findByUserId(userId).orElseThrow(() -> new AppException(
                HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "Notification settings are unavailable"));
    }

    private static UserProfileResponse toProfile(UserRecord user, UserPreferencesRecord prefs) {
        return new UserProfileResponse(user.id(), user.username(), user.email(), prefs.displayName(), user.role(),
                user.status(), prefs.currencyCode(), prefs.timeZone(), prefs.weekStartsOn(), user.createdAt(), user.updatedAt());
    }

    private static UserPreferencesResponse toPreferences(UserPreferencesRecord prefs) {
        return new UserPreferencesResponse(prefs.userId(), prefs.displayName(), prefs.currencyCode(),
                prefs.timeZone(), prefs.weekStartsOn(), prefs.updatedAt());
    }

    private static NotificationSettingsResponse toNotifications(NotificationSettingsRecord settings) {
        return new NotificationSettingsResponse(settings.notificationsEnabled(), settings.dailyReminderEnabled(),
                settings.dailyReminderTime(), settings.budgetAlertEnabled(), settings.budgetAlertThresholdPercent(),
                settings.budgetExceedAlertEnabled(), settings.otherNotificationsEnabled(), settings.updatedAt());
    }

    private static String normalizedDisplayName(String value) {
        String normalized = value.trim();
        if (normalized.isEmpty()) throw badRequest("displayName must not be blank");
        return normalized;
    }

    private static String validTimeZone(String value) {
        String normalized = value.trim();
        try {
            return ZoneId.of(normalized).getId();
        } catch (DateTimeException exception) {
            throw badRequest("Invalid timeZone");
        }
    }

    private static boolean valueOr(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    private static AppException badRequest(String message) {
        return new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message);
    }

    private static AppException notFound() {
        return new AppException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "User not found");
    }
}
