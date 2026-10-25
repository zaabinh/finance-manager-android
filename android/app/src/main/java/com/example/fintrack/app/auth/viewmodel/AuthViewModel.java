package com.example.fintrack.app.auth.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.fintrack.app.auth.remote.LoginResponse;
import com.example.fintrack.app.auth.repository.AuthRepository;
import com.example.fintrack.app.core.network.Resource;

public class AuthViewModel extends AndroidViewModel {
    private final AuthRepository authRepository;

    public AuthViewModel(@NonNull Application application) {
        super(application);
        this.authRepository = new AuthRepository(application);
    }

    public LiveData<Resource<LoginResponse>> login(String usernameOrEmail, String password) {
        return authRepository.login(usernameOrEmail, password);
    }

    public LiveData<Resource<Void>> register(String username, String email, String password) {
        return authRepository.register(username, email, password);
    }

    public LiveData<Resource<Void>> verifyEmail(String email, String otp) {
        return authRepository.verifyEmail(email, otp);
    }

    public LiveData<Resource<Void>> forgotPassword(String email) {
        return authRepository.forgotPassword(email);
    }

    public LiveData<Resource<Void>> resetPassword(String email, String newPassword) {
        return authRepository.resetPassword(email, newPassword);
    }
}
