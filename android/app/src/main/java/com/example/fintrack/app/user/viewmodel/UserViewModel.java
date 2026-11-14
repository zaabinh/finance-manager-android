package com.example.fintrack.app.user.viewmodel;

import com.example.fintrack.app.core.network.RequestHandle;

import androidx.lifecycle.ViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.SessionSnapshot;
import com.example.fintrack.app.core.session.SessionStore;
import com.example.fintrack.app.core.util.Event;
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
 * Hồ sơ, trạng thái setup và việc xóa phiên khi vô hiệu hóa tài khoản được ghi tại đây qua SessionStore,
 * luôn gắn với phiên của lúc gửi yêu cầu: phản hồi của một phiên cũ bị bỏ qua (không ghi, không phát lên UI).
 */
public class UserViewModel extends ViewModel {

    private final UserRepository userRepository;
    private RequestHandle profileRead = RequestHandle.NONE;
    private RequestHandle settingsRead = RequestHandle.NONE;
    private RequestHandle notificationRead = RequestHandle.NONE;
    private final SessionStore sessionStore;
    private final MutableLiveData<Resource<UserProfileResponse>> profileResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<UserProfileResponse>> updateProfileResult = new MutableLiveData<>();
    private final MutableLiveData<Event<Resource<Void>>> deactivateAccountResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<FirstSetupResponse>> firstSetupResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<UserSettingsResponse>> settingsResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<NotificationSettingsResponse>> notificationSettingsResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<NotificationSettingsResponse>> updateNotificationSettingsResult = new MutableLiveData<>();

    public UserViewModel(UserRepository userRepository, SessionStore sessionStore) {
        this.userRepository = userRepository;
        this.sessionStore = sessionStore;
    }

    public LiveData<Resource<UserProfileResponse>> getProfileResult() {
        return profileResult;
    }

    public LiveData<Resource<UserProfileResponse>> getUpdateProfileResult() {
        return updateProfileResult;
    }

    /** Sự kiện một lần: SUCCESS nghĩa là tài khoản đã bị vô hiệu hóa và phiên local của nó đã được xóa. */
    public LiveData<Event<Resource<Void>>> getDeactivateAccountResult() {
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
        SessionSnapshot origin = sessionStore.current();
        profileResult.setValue(Resource.loading(null));
        profileRead.cancel();
        profileRead = userRepository.getProfile(result -> {
            if (!sessionStore.isCurrent(origin)) return;
            UserProfileResponse profile = result.getData();
            if (result.getStatus() == Resource.Status.SUCCESS && profile != null && !isEmpty(profile.getDisplayName()))
                sessionStore.updateProfile(origin, profile.getId(), profile.getEmail(), profile.getDisplayName());
            profileResult.setValue(result);
        });
    }

    public void updateProfile(String displayName, String timeZone) {
        SessionSnapshot origin = sessionStore.current();
        updateProfileResult.setValue(Resource.loading(null));
        userRepository.updateProfile(displayName, timeZone, result -> {
            if (!sessionStore.isCurrent(origin)) return;
            UserProfileResponse profile = result.getData();
            if (result.getStatus() == Resource.Status.SUCCESS && profile != null && !isEmpty(profile.getDisplayName())) {
                // Chỉ đổi tên hiển thị; userId/email giữ theo phiên hiện tại (ghi bị từ chối nếu phiên đã đổi).
                SessionSnapshot now = sessionStore.current();
                sessionStore.updateProfile(origin, now.userId, now.email, profile.getDisplayName());
            }
            updateProfileResult.setValue(result);
        });
    }

    /** Vô hiệu hóa tài khoản; thành công thì xóa đúng phiên của lúc người dùng xác nhận. */
    public void deactivateAccount(String currentPassword) {
        SessionSnapshot origin = sessionStore.current();
        userRepository.deactivateAccount(currentPassword, result -> {
            if (!sessionStore.isCurrent(origin)) return;
            if (result.getStatus() == Resource.Status.SUCCESS) sessionStore.clear(origin);
            deactivateAccountResult.setValue(new Event<>(result));
        });
    }

    public void fetchSettings() {
        SessionSnapshot origin = sessionStore.current();
        settingsResult.setValue(Resource.loading(null));
        settingsRead.cancel();
        settingsRead = userRepository.getSettings(result -> {
            if (!sessionStore.isCurrent(origin)) return;
            UserProfileResponse profile = result.getData() == null ? null : result.getData().getProfile();
            if (result.getStatus() == Resource.Status.SUCCESS && profile != null)
                sessionStore.updateProfile(origin, profile.getId(), profile.getEmail(), profile.getDisplayName());
            settingsResult.setValue(result);
        });
    }

    public void fetchNotificationSettings() {
        SessionSnapshot origin = sessionStore.current();
        notificationSettingsResult.setValue(Resource.loading(null));
        notificationRead.cancel();
        notificationRead = userRepository.getNotificationSettings(result -> {
            if (sessionStore.isCurrent(origin)) notificationSettingsResult.setValue(result);
        });
    }

    public void updateNotificationSettings(UpdateNotificationSettingsRequest request) {
        SessionSnapshot origin = sessionStore.current();
        updateNotificationSettingsResult.setValue(Resource.loading(null));
        userRepository.updateNotificationSettings(request, result -> {
            if (sessionStore.isCurrent(origin)) updateNotificationSettingsResult.setValue(result);
        });
    }

    public LiveData<Resource<FirstSetupResponse>> getFirstSetupResult() { return firstSetupResult; }

    /** Gửi thiết lập lần đầu; server xác nhận thì đánh dấu phiên đã hoàn tất setup. */
    public void completeFirstSetup(FirstSetupRequest request) {
        SessionSnapshot origin = sessionStore.current();
        firstSetupResult.setValue(Resource.loading(null));
        userRepository.completeFirstSetup(request, result -> {
            if (!sessionStore.isCurrent(origin)) return;
            if (result.getStatus() == Resource.Status.SUCCESS) sessionStore.completeSetup(origin);
            firstSetupResult.setValue(result);
        });
    }

    /** Người dùng bấm "Vào ứng dụng" ở màn hình hoàn tất: lưu trạng thái setup và tên hiển thị vào phiên hiện tại. */
    public void confirmSetupCompleted(String displayName) {
        SessionSnapshot origin = sessionStore.current();
        sessionStore.completeSetup(origin);
        sessionStore.updateProfile(origin, origin.userId, origin.email, displayName);
    }

    // android.text.TextUtils không dùng được trong JVM unit test.
    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
    @Override protected void onCleared() {
        profileRead.cancel(); settingsRead.cancel(); notificationRead.cancel();
    }
}
