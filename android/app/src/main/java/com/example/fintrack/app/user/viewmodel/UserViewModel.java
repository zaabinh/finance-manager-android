package com.example.fintrack.app.user.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.user.remote.UserProfileResponse;
import com.example.fintrack.app.user.repository.UserRepository;

public class UserViewModel extends AndroidViewModel {

    private final UserRepository userRepository;
    private final MutableLiveData<Resource<UserProfileResponse>> profileResult = new MutableLiveData<>();

    public UserViewModel(@NonNull Application application) {
        super(application);
        this.userRepository = new UserRepository(application);
    }

    public LiveData<Resource<UserProfileResponse>> getProfileResult() {
        return profileResult;
    }

    public void fetchProfile() {
        profileResult.setValue(Resource.loading(null));
        userRepository.getProfile(profileResult);
    }
}
