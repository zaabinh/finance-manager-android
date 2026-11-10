package com.example.fintrack.app.user.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.user.remote.FirstSetupRequest;
import com.example.fintrack.app.user.remote.FirstSetupResponse;
import com.example.fintrack.app.user.remote.NotificationSettingsResponse;
import com.example.fintrack.app.user.remote.UpdateNotificationSettingsRequest;
import com.example.fintrack.app.user.remote.UserProfileResponse;
import com.example.fintrack.app.user.remote.UserSettingsResponse;
import com.example.fintrack.app.user.repository.UserRepository;

/**
 * ViewModel quản lý dữ liệu hồ sơ người dùng (User Profile)
 * làm cầu nối giữa Giao diện (Activity/Fragment) và Repository.
 */
public class UserViewModel extends AndroidViewModel {

    private final UserRepository userRepository;
    private final MutableLiveData<Resource<UserProfileResponse>> profileResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<UserProfileResponse>> updateProfileResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> deactivateAccountResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<FirstSetupResponse>> firstSetupResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<UserSettingsResponse>> settingsResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<NotificationSettingsResponse>> notificationSettingsResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<NotificationSettingsResponse>> updateNotificationSettingsResult = new MutableLiveData<>();

    public UserViewModel(@NonNull Application application) {
        super(application);
        this.userRepository = new UserRepository(application);
    }

    public LiveData<Resource<UserProfileResponse>> getProfileResult() {
        return profileResult;
    }

    public LiveData<Resource<UserProfileResponse>> getUpdateProfileResult() {
        return updateProfileResult;
    }

    public LiveData<Resource<Void>> getDeactivateAccountResult() {
        return deactivateAccountResult;
    }

    public LiveData<Resource<UserSettingsResponse>> getSettingsResult() {
        return settingsResult;
    }

    public LiveData<Resource<NotificationSettingsResponse>> getNotificationSettingsResult() {
        return notificationSettingsResult;
    }

    public LiveData<Resource<NotificationSettingsResponse>> getUpdateNotificationSettingsResult() {
        return updateNotificationSettingsResult;
    }

    public void fetchProfile() {
        profileResult.setValue(Resource.loading(null));
        userRepository.getProfile(profileResult);
    }

    public void updateProfile(String name, String phone) {
        updateProfileResult.setValue(Resource.loading(null));
        userRepository.updateProfile(name, phone, updateProfileResult);
    }

    public void deactivateAccount(String currentPassword) {
        deactivateAccountResult.setValue(Resource.loading(null));
        userRepository.deactivateAccount(currentPassword, deactivateAccountResult);
    }

    public void fetchSettings() {
        settingsResult.setValue(Resource.loading(null));
        userRepository.getSettings(settingsResult);
    }

    public void fetchNotificationSettings() {
        notificationSettingsResult.setValue(Resource.loading(null));
        userRepository.getNotificationSettings(notificationSettingsResult);
    }

    public void updateNotificationSettings(UpdateNotificationSettingsRequest request) {
        updateNotificationSettingsResult.setValue(Resource.loading(null));
        userRepository.updateNotificationSettings(request, updateNotificationSettingsResult);
    }

    public LiveData<Resource<FirstSetupResponse>> getFirstSetupResult() { return firstSetupResult; }

    public void completeFirstSetup(FirstSetupRequest request) {
        firstSetupResult.setValue(Resource.loading(null));
        userRepository.completeFirstSetup(request, firstSetupResult);
    }
}
