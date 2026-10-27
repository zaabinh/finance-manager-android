package com.example.fintrack.app.auth.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fintrack.app.auth.remote.LoginResponse;
import com.example.fintrack.app.auth.repository.AuthRepository;
import com.example.fintrack.app.core.network.Resource;

public class AuthViewModel extends AndroidViewModel {
    private final AuthRepository authRepository;

    private final MutableLiveData<Resource<LoginResponse>> loginResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> registerResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> verifyEmailResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> resendOtpResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> forgotPasswordResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> resetPasswordResult = new MutableLiveData<>();

    private String registeredEmail = "";
    private String forgotPasswordEmail = "";

    public AuthViewModel(@NonNull Application application) {
        super(application);
        this.authRepository = new AuthRepository(application);
    }

    public String getRegisteredEmail() {
        return registeredEmail;
    }

    public void setRegisteredEmail(String registeredEmail) {
        this.registeredEmail = registeredEmail;
    }

    public String getForgotPasswordEmail() {
        return forgotPasswordEmail;
    }

    public void setForgotPasswordEmail(String forgotPasswordEmail) {
        this.forgotPasswordEmail = forgotPasswordEmail;
    }

    public LiveData<Resource<LoginResponse>> getLoginResult() {
        return loginResult;
    }

    public LiveData<Resource<Void>> getRegisterResult() {
        return registerResult;
    }

    public LiveData<Resource<Void>> getVerifyEmailResult() {
        return verifyEmailResult;
    }

    public LiveData<Resource<Void>> getResendOtpResult() {
        return resendOtpResult;
    }

    public LiveData<Resource<Void>> getForgotPasswordResult() {
        return forgotPasswordResult;
    }

    public LiveData<Resource<Void>> getResetPasswordResult() {
        return resetPasswordResult;
    }

    public void login(String usernameOrEmail, String password) {
        loginResult.setValue(Resource.loading(null));
        authRepository.login(usernameOrEmail, password, loginResult);
    }

    public void register(String username, String email, String password) {
        this.registeredEmail = email;
        registerResult.setValue(Resource.loading(null));
        authRepository.register(username, email, password, registerResult);
    }

    public void verifyEmail(String email, String otp) {
        verifyEmailResult.setValue(Resource.loading(null));
        authRepository.verifyEmail(email, otp, verifyEmailResult);
    }

    public void resendOtp(String email) {
        resendOtpResult.setValue(Resource.loading(null));
        authRepository.resendOtp(email, resendOtpResult);
    }

    public void forgotPassword(String email) {
        this.forgotPasswordEmail = email;
        forgotPasswordResult.setValue(Resource.loading(null));
        authRepository.forgotPassword(email, forgotPasswordResult);
    }

    public void resetPassword(String email, String otp, String newPassword) {
        resetPasswordResult.setValue(Resource.loading(null));
        authRepository.resetPassword(email, otp, newPassword, resetPasswordResult);
    }
}
