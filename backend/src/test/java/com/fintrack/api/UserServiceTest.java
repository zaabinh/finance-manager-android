package com.fintrack.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.api.dto.request.user.UpdateNotificationSettingsRequest;
import com.fintrack.api.dto.request.user.UpdateProfileRequest;
import com.fintrack.api.entity.enums.UserRole;
import com.fintrack.api.entity.enums.UserStatus;
import com.fintrack.api.exception.AppException;
import com.fintrack.api.repository.NotificationSettingsRepository;
import com.fintrack.api.repository.NotificationSettingsRepository.NotificationSettingsRecord;
import com.fintrack.api.repository.UserPreferencesRepository;
import com.fintrack.api.repository.UserPreferencesRepository.UserPreferencesRecord;
import com.fintrack.api.repository.UserRepository;
import com.fintrack.api.repository.UserRepository.UserRecord;
import com.fintrack.api.service.auth.RefreshTokenRotatingService;
import com.fintrack.api.service.user.UserService;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final UserPreferencesRepository preferences = mock(UserPreferencesRepository.class);
    private final NotificationSettingsRepository notifications = mock(NotificationSettingsRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final RefreshTokenRotatingService refreshTokens = mock(RefreshTokenRotatingService.class);
    private final UUID userId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-05T00:00:00Z");
    private UserService service;
    private UserRecord user;

    @BeforeEach
    void setUp() {
        service = new UserService(users, preferences, notifications, passwords, refreshTokens);
        user = new UserRecord(userId, "minhanh", "minh@example.com", "hash", UserStatus.ACTIVE,
                UserRole.USER, "Minh Anh", true, true, now, now);
        when(users.findById(userId)).thenReturn(Optional.of(user));
    }

    @Test
    void combinedSettingsReturnsAllThreeSections() {
        when(preferences.findByUserId(userId)).thenReturn(Optional.of(prefs("Minh Anh", "Asia/Ho_Chi_Minh")));
        when(notifications.findByUserId(userId)).thenReturn(Optional.of(notificationSettings(80)));

        var response = service.getMySettings(userId);

        assertThat(response.profile().displayName()).isEqualTo("Minh Anh");
        assertThat(response.preferences().timeZone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(response.notificationSettings().budgetAlertThresholdPercent()).isEqualTo((short) 80);
    }

    @Test
    void profileAndPreferencesFallbackToUsernameWhenDisplayNameIsUnset() {
        when(preferences.findByUserId(userId))
                .thenReturn(Optional.of(prefs(null, "Asia/Ho_Chi_Minh")));
        when(notifications.findByUserId(userId)).thenReturn(Optional.of(notificationSettings(80)));

        assertThat(service.getMyProfile(userId).displayName()).isEqualTo("minhanh");
        assertThat(service.getMyPreferences(userId).displayName()).isEqualTo("minhanh");
        var settings = service.getMySettings(userId);
        assertThat(settings.profile().displayName()).isEqualTo("minhanh");
        assertThat(settings.preferences().displayName()).isEqualTo("minhanh");
    }

    @Test
    void profilePatchUpdatesDisplayNameAndTimeZoneInPreferences() {
        when(preferences.findByUserId(userId))
                .thenReturn(Optional.of(prefs("Minh Anh", "Asia/Ho_Chi_Minh")))
                .thenReturn(Optional.of(prefs("An", "Asia/Tokyo")));

        var response = service.updateMyProfile(userId, new UpdateProfileRequest(" An ", "Asia/Tokyo"));

        verify(preferences).upsert(userId, "An", "VND", "Asia/Tokyo", (short) 1);
        assertThat(response.displayName()).isEqualTo("An");
        assertThat(response.timeZone()).isEqualTo("Asia/Tokyo");
    }

    @Test
    void profilePatchRejectsUnknownTimeZone() {
        when(preferences.findByUserId(userId)).thenReturn(Optional.of(prefs("Minh Anh", "Asia/Ho_Chi_Minh")));

        assertThatThrownBy(() -> service.updateMyProfile(userId, new UpdateProfileRequest(null, "Mars/Olympus")))
                .isInstanceOf(AppException.class)
                .hasMessage("Invalid timeZone");
    }

    @Test
    void notificationPatchKeepsUnspecifiedValues() {
        when(notifications.findByUserId(userId))
                .thenReturn(Optional.of(notificationSettings(80)))
                .thenReturn(Optional.of(notificationSettings(90)));

        var response = service.updateMyNotificationSettings(userId,
                new UpdateNotificationSettingsRequest(null, null, null, null, (short) 90, null, null));

        verify(notifications).upsert(userId, true, true, LocalTime.of(20, 0), true,
                (short) 90, true, true);
        assertThat(response.budgetAlertThresholdPercent()).isEqualTo((short) 90);
    }

    private UserPreferencesRecord prefs(String displayName, String timeZone) {
        return new UserPreferencesRecord(userId, displayName, "VND", timeZone, (short) 1, now);
    }

    private NotificationSettingsRecord notificationSettings(int threshold) {
        return new NotificationSettingsRecord(userId, true, true, LocalTime.of(20, 0), true,
                (short) threshold, true, true, now);
    }
}
