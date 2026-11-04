package com.example.fintrack.app.auth.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fintrack.app.auth.remote.LoginResponse;
import com.example.fintrack.app.auth.repository.AuthRepository;
import com.example.fintrack.app.core.network.Resource;

/**
 * ViewModel quản lý logic liên quan đến xác thực (Auth),
 * làm cầu nối giữa UI (Activity/Fragment) và Repository.
 */
public class AuthViewModel extends AndroidViewModel {

    // Repository chịu trách nhiệm gọi API hoặc lấy dữ liệu từ local
    private final AuthRepository authRepository;

    // Các LiveData lưu trữ trạng thái kết quả của các chức năng (Loading, Success, Error)
    private final MutableLiveData<Resource<LoginResponse>> loginResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> registerResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> verifyEmailResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> resendOtpResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> forgotPasswordResult = new MutableLiveData<>();
    private final MutableLiveData<Resource<Void>> resetPasswordResult = new MutableLiveData<>();

    // Biến tạm để lưu trữ thông tin email và mã OTP giữa các màn hình
    private String registeredEmail = "";
    private String forgotPasswordEmail = "";
    private String resetOtp = "";

    // Khởi tạo ViewModel và Repository
    public AuthViewModel(@NonNull Application application) {
        super(application);
        this.authRepository = new AuthRepository(application);
    }

    // --- Các hàm Getter & Setter để lưu và lấy dữ liệu tạm ---

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

    public String getResetOtp() {
        return resetOtp;
    }

    public void setResetOtp(String resetOtp) {
        this.resetOtp = resetOtp;
    }

    // --- Các hàm Getter trả về LiveData để UI quan sát (observe) thay đổi ---

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

    // --- Các hàm xử lý nghiệp vụ xác thực ---

    /** Thực hiện đăng nhập */
    public void login(String usernameOrEmail, String password) {
        loginResult.setValue(Resource.loading(null)); // Đặt trạng thái là Đang tải
        authRepository.login(usernameOrEmail, password, loginResult);
    }

    /** Thực hiện đăng ký tài khoản mới */
    public void register(String username, String email, String password) {
        this.registeredEmail = email; // Lưu lại email để dùng cho việc xác thực OTP sau đó
        registerResult.setValue(Resource.loading(null));
        authRepository.register(username, email, password, registerResult);
    }

    /** Xác thực email bằng mã OTP */
    public void verifyEmail(String email, String otp) {
        verifyEmailResult.setValue(Resource.loading(null));
        authRepository.verifyEmail(email, otp, verifyEmailResult);
    }

    /** Gửi lại mã OTP */
    public void resendOtp(String email) {
        resendOtpResult.setValue(Resource.loading(null));
        authRepository.resendOtp(email, resendOtpResult);
    }

    /** Yêu cầu quên mật khẩu (gửi mã OTP về email) */
    public void forgotPassword(String email) {
        this.forgotPasswordEmail = email; // Lưu lại email dùng cho bước đặt lại mật khẩu
        forgotPasswordResult.setValue(Resource.loading(null));
        authRepository.forgotPassword(email, forgotPasswordResult);
    }

    /** Đặt lại mật khẩu mới bằng email, mã OTP và mật khẩu mới */
    public void resetPassword(String email, String otp, String newPassword) {
        resetPasswordResult.setValue(Resource.loading(null));
        authRepository.resetPassword(email, otp, newPassword, resetPasswordResult);
    }
}