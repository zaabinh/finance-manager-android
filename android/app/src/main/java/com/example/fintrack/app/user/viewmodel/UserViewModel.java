package com.example.fintrack.app.user.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.user.remote.UserProfileResponse;
import com.example.fintrack.app.user.remote.UserSettingsResponse;
import com.example.fintrack.app.user.remote.NotificationSettingsResponse;
import com.example.fintrack.app.user.remote.UpdateNotificationSettingsRequest;
import com.example.fintrack.app.user.repository.UserRepository;

/**
 * ViewModel quản lý dữ liệu hồ sơ người dùng (User Profile)
 * làm cầu nối giữa Giao diện (Activity/Fragment) và Repository.
 */
public class UserViewModel extends AndroidViewModel {

    private final UserRepository userRepository;
    private final MutableLiveData<Resource<UserProfileResponse>> profileResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<UserProfileResponse>> updateProfileResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> deleteAccountResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<UserSettingsResponse>> settingsResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<NotificationSettingsResponse>> notificationSettingsResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<NotificationSettingsResponse>> updateNotificationSettingsResult = new MutableLiveData<>();

    public UserViewModel(@NonNull Application application) {
        super(application);
        this.userRepository = new UserRepository(application);
    }

    /** Trả về LiveData kết quả lấy thông tin hồ sơ */
    public LiveData<Resource<UserProfileResponse>> getProfileResult() {
        return profileResult;
    }

    /** Trả về LiveData kết quả cập nhật hồ sơ */
    public LiveData<Resource<UserProfileResponse>> getUpdateProfileResult() {
        return updateProfileResult;
    }

    /** Trả về LiveData kết quả xóa tài khoản */
    public LiveData<Resource<Void>> getDeleteAccountResult() {
        return deleteAccountResult;
    }

    /** Yêu cầu lấy thông tin hồ sơ mới nhất từ Server */
    public void fetchProfile() {
        profileResult.setValue(Resource.loading(null));
        userRepository.getProfile(profileResult);
    }

    /** Yêu cầu cập nhật họ tên và số điện thoại lên Server */
    public void updateProfile(String displayName, String timeZone) {
        updateProfileResult.setValue(Resource.loading(null));
        userRepository.updateProfile(displayName, timeZone, updateProfileResult);
    }

    public LiveData<Resource<UserSettingsResponse>> getSettingsResult() { return settingsResult; }
    public LiveData<Resource<NotificationSettingsResponse>> getNotificationSettingsResult() {
        return notificationSettingsResult;
    }
    public LiveData<Resource<NotificationSettingsResponse>> getUpdateNotificationSettingsResult() {
        return updateNotificationSettingsResult;
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

    /** Yêu cầu xóa vĩnh viễn tài khoản người dùng khỏi Server */
    public void deleteAccount() {
        deleteAccountResult.setValue(Resource.loading(null));
        userRepository.deleteAccount(deleteAccountResult);
    }
}
