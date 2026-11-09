package com.fintrack.api.controller;

import com.fintrack.api.dto.response.ApiResponse;
import com.fintrack.api.security.AuthenticatedUser;
import com.fintrack.api.service.notification.NotificationSettingsV2Service;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!test")
@RequestMapping("/api/v2/users/me/notification-settings")
@io.swagger.v3.oas.annotations.tags.Tag(name="Notifications settings v2")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
public class NotificationSettingsV2Controller {
    private final NotificationSettingsV2Service settings;
    public NotificationSettingsV2Controller(NotificationSettingsV2Service settings) { this.settings=settings; }
    @GetMapping public ApiResponse<NotificationSettingsV2Service.Settings> get(@AuthenticationPrincipal AuthenticatedUser user) {
        return new ApiResponse<>(true,"Notification settings",settings.get(user.id()),200);
    }
    @PatchMapping public ApiResponse<NotificationSettingsV2Service.Settings> patch(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody NotificationSettingsV2Service.Patch request) {
        return new ApiResponse<>(true,"Notification settings updated",settings.patch(user.id(),request),200);
    }
}
