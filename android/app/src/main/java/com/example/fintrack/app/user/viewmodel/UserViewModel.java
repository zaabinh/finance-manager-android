package com.example.fintrack.app.user.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.user.remote.UserProfileResponse;
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
    public void updateProfile(String name, String phone) {
        updateProfileResult.setValue(Resource.loading(null));
        userRepository.updateProfile(name, phone, updateProfileResult);
    }

    /** Yêu cầu xóa vĩnh viễn tài khoản người dùng khỏi Server */
    public void deleteAccount() {
        deleteAccountResult.setValue(Resource.loading(null));
        userRepository.deleteAccount(deleteAccountResult);
    }
}
