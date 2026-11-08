package com.fintrack.api.controller;

import com.fintrack.api.dto.request.user.DeactivateUserRequest;
import com.fintrack.api.dto.request.user.UpdateNotificationSettingsRequest;
import com.fintrack.api.dto.request.user.UpdatePreferencesRequest;
import com.fintrack.api.dto.request.user.UpdateProfileRequest;
import com.fintrack.api.dto.response.ApiResponse;
import com.fintrack.api.dto.response.user.NotificationSettingsResponse;
import com.fintrack.api.dto.response.user.UserPreferencesResponse;
import com.fintrack.api.dto.response.user.UserProfileResponse;
import com.fintrack.api.dto.response.user.UserSettingsResponse;
import com.fintrack.api.security.AuthenticatedUser;
import com.fintrack.api.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!test")
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "User Profile & Settings")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final UserService users;

    @GetMapping
    public ApiResponse<UserProfileResponse> getProfile(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ok("User profile", users.getMyProfile(principal.id()));
    }

    @PatchMapping
    public ApiResponse<UserProfileResponse> updateProfile(@AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ok("User profile updated", users.updateMyProfile(principal.id(), request));
    }

    @GetMapping("/settings")
    public ApiResponse<UserSettingsResponse> getSettings(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ok("User settings", users.getMySettings(principal.id()));
    }

    @GetMapping("/preferences")
    public ApiResponse<UserPreferencesResponse> getPreferences(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ok("User preferences", users.getMyPreferences(principal.id()));
    }

    @PatchMapping("/preferences")
    public ApiResponse<UserPreferencesResponse> updatePreferences(@AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdatePreferencesRequest request) {
        return ok("User preferences updated", users.updateMyPreferences(principal.id(), request));
    }

    @GetMapping("/notification-settings")
    public ApiResponse<NotificationSettingsResponse> getNotificationSettings(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ok("Notification settings", users.getMyNotificationSettings(principal.id()));
    }

    @PatchMapping("/notification-settings")
    public ApiResponse<NotificationSettingsResponse> updateNotificationSettings(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateNotificationSettingsRequest request) {
        return ok("Notification settings updated", users.updateMyNotificationSettings(principal.id(), request));
    }

    @DeleteMapping
    public ResponseEntity<Void> deactivate(@AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody DeactivateUserRequest request) {
        users.deactivateMyAccount(principal.id(), request);
        return ResponseEntity.noContent().build();
    }

    private static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, 200);
    }
}
