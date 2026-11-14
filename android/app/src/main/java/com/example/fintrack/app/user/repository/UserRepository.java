package com.example.fintrack.app.user.repository;

import com.example.fintrack.app.user.remote.DeactivateUserRequest;
import com.example.fintrack.app.user.remote.FirstSetupRequest;
import com.example.fintrack.app.user.remote.FirstSetupResponse;
import com.example.fintrack.app.user.remote.NotificationSettingsResponse;
import com.example.fintrack.app.user.remote.UpdateNotificationSettingsRequest;
import com.example.fintrack.app.user.remote.UpdateProfileRequest;
import com.example.fintrack.app.user.remote.UserApiService;
import com.example.fintrack.app.user.remote.UserProfileResponse;
import com.example.fintrack.app.user.remote.UserSettingsResponse;
import com.example.fintrack.app.core.network.ApiCalls;
import com.example.fintrack.app.core.network.RequestHandle;
import com.example.fintrack.app.core.network.Resource;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Transport only. The ViewModel owns state and session side effects. */
public class UserRepository {
    private final UserApiService apiService;
    private final Executor delivery;
    public UserRepository(UserApiService apiService, Executor delivery) {
        this.apiService = apiService; this.delivery = delivery;
    }
    public RequestHandle getProfile(Consumer<Resource<UserProfileResponse>> result) {
        return ApiCalls.enqueue(apiService.getProfile(), true, delivery, result);
    }
    public RequestHandle updateProfile(String displayName, String timeZone, Consumer<Resource<UserProfileResponse>> result) {
        return ApiCalls.enqueue(apiService.updateProfile(new UpdateProfileRequest(displayName, timeZone)), true, delivery, result);
    }
    public RequestHandle deactivateAccount(String currentPassword, Consumer<Resource<Void>> result) {
        return ApiCalls.enqueue(apiService.deactivateAccount(new DeactivateUserRequest(currentPassword)), false, delivery, result);
    }
    public RequestHandle getSettings(Consumer<Resource<UserSettingsResponse>> result) {
        return ApiCalls.enqueue(apiService.getSettings(), true, delivery, result);
    }
    public RequestHandle getNotificationSettings(Consumer<Resource<NotificationSettingsResponse>> result) {
        return ApiCalls.enqueue(apiService.getNotificationSettings(), true, delivery, result);
    }
    public RequestHandle updateNotificationSettings(UpdateNotificationSettingsRequest request, Consumer<Resource<NotificationSettingsResponse>> result) {
        return ApiCalls.enqueue(apiService.updateNotificationSettings(request), true, delivery, result);
    }
    public RequestHandle completeFirstSetup(FirstSetupRequest request, Consumer<Resource<FirstSetupResponse>> result) {
        return ApiCalls.enqueue(apiService.completeFirstSetup(request), true, delivery, result);
    }
}
