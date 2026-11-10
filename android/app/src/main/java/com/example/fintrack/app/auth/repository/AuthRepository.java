package com.example.fintrack.app.auth.repository;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.example.fintrack.R;
import com.example.fintrack.app.auth.remote.AuthApiService;
import com.example.fintrack.app.auth.remote.ChangePasswordRequest;
import com.example.fintrack.app.auth.remote.LoginRequest;
import com.example.fintrack.app.auth.remote.LoginResponse;
import com.example.fintrack.app.auth.remote.RefreshTokenRequest;
import com.example.fintrack.app.auth.remote.RegisterRequest;
import com.example.fintrack.app.auth.remote.ResendOtpRequest;
import com.example.fintrack.app.auth.remote.ResetPasswordRequest;
import com.example.fintrack.app.auth.remote.TokenResponse;
import com.example.fintrack.app.auth.remote.VerifyEmailRequest;
import com.example.fintrack.app.core.network.ApiClient;
import com.example.fintrack.app.core.network.ApiResponse;
import com.example.fintrack.app.core.network.ErrorMapper;
import com.example.fintrack.app.core.network.Resource;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository chịu trách nhiệm xử lý các cuộc gọi API mạng liên quan đến Xác thực (Auth),
 * chuyển đổi kết quả về dạng LiveData Resource cho ViewModel.
 */
public class AuthRepository {

    private final Context appContext;
    private final AuthApiService apiService;

    public AuthRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.apiService = ApiClient.createService(appContext, AuthApiService.class);
    }

    /** Gọi API Đăng nhập tài khoản */
    public void login(String usernameOrEmail, String password, boolean rememberMe, MutableLiveData<Resource<LoginResponse>> result) {
        apiService.login(new LoginRequest(usernameOrEmail, password, rememberMe)).enqueue(new Callback<ApiResponse<LoginResponse>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<LoginResponse>> call, @NonNull Response<ApiResponse<LoginResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<LoginResponse> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        result.setValue(Resource.success(apiResponse.getData()));
                    } else {
                        String msg = !TextUtils.isEmpty(apiResponse.getMessage()) ? apiResponse.getMessage() : appContext.getString(R.string.login_failed);
                        result.setValue(Resource.error(msg, null));
                    }
                } else {
                    result.setValue(Resource.error(ErrorMapper.getHttpErrorMessage(appContext, response), null));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<LoginResponse>> call, @NonNull Throwable t) {
                result.setValue(Resource.error(ErrorMapper.getErrorMessage(appContext, t), null));
            }
        });
    }

    /** Gọi API Đăng ký tài khoản mới */
    public void register(String username, String email, String password, MutableLiveData<Resource<Void>> result) {
        apiService.register(new RegisterRequest(username, email, password)).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful() || response.code() == 201) {
                    result.setValue(Resource.success(null));
                } else {
                    result.setValue(Resource.error(ErrorMapper.getHttpErrorMessage(appContext, response), null));
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                result.setValue(Resource.error(ErrorMapper.getErrorMessage(appContext, t), null));
            }
        });
    }

    /** Gọi API Xác thực email bằng mã OTP */
    public void verifyEmail(String email, String otp, MutableLiveData<Resource<Void>> result) {
        apiService.verifyEmail(new VerifyEmailRequest(email, otp)).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful() || response.code() == 204) {
                    result.setValue(Resource.success(null));
                } else {
                    result.setValue(Resource.error(ErrorMapper.getHttpErrorMessage(appContext, response), null));
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                result.setValue(Resource.error(ErrorMapper.getErrorMessage(appContext, t), null));
            }
        });
    }

    /** Gọi API Gửi lại email xác nhận tài khoản (/resend-verification) */
    public void resendVerification(String email, MutableLiveData<Resource<Void>> result) {
        apiService.resendVerification(new ResendOtpRequest(email)).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful() || response.code() == 202) {
                    result.setValue(Resource.success(null));
                } else {
                    result.setValue(Resource.error(ErrorMapper.getHttpErrorMessage(appContext, response), null));
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                result.setValue(Resource.error(ErrorMapper.getErrorMessage(appContext, t), null));
            }
        });
    }

    /** Gọi API Gửi lại mã OTP */
    public void resendOtp(String email, MutableLiveData<Resource<Void>> result) {
        resendVerification(email, result);
    }

    /** Gọi API Yêu cầu quên mật khẩu (gửi OTP về email) */
    public void forgotPassword(String email, MutableLiveData<Resource<Void>> result) {
        apiService.forgotPassword(new ResendOtpRequest(email)).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful() || response.code() == 202) {
                    result.setValue(Resource.success(null));
                } else {
                    result.setValue(Resource.error(ErrorMapper.getHttpErrorMessage(appContext, response), null));
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                result.setValue(Resource.error(ErrorMapper.getErrorMessage(appContext, t), null));
            }
        });
    }

    /** Gọi API Đặt lại mật khẩu mới */
    public void resetPassword(String email, String otp, String newPassword, MutableLiveData<Resource<Void>> result) {
        apiService.resetPassword(new ResetPasswordRequest(email, otp, newPassword)).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful() || response.code() == 204) {
                    result.setValue(Resource.success(null));
                } else {
                    result.setValue(Resource.error(ErrorMapper.getHttpErrorMessage(appContext, response), null));
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                result.setValue(Resource.error(ErrorMapper.getErrorMessage(appContext, t), null));
            }
        });
    }

    /** Gọi API Đổi mật khẩu (/change-password) */
    public void changePassword(String currentPassword, String newPassword, MutableLiveData<Resource<TokenResponse>> result) {
        apiService.changePassword(new ChangePasswordRequest(currentPassword, newPassword)).enqueue(new Callback<ApiResponse<TokenResponse>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<TokenResponse>> call, @NonNull Response<ApiResponse<TokenResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<TokenResponse> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        result.setValue(Resource.success(apiResponse.getData()));
                    } else {
                        String msg = !TextUtils.isEmpty(apiResponse.getMessage()) ? apiResponse.getMessage() : appContext.getString(R.string.reset_password_failed);
                        result.setValue(Resource.error(msg, null));
                    }
                } else {
                    result.setValue(Resource.error(ErrorMapper.getHttpErrorMessage(appContext, response), null));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<TokenResponse>> call, @NonNull Throwable t) {
                result.setValue(Resource.error(ErrorMapper.getErrorMessage(appContext, t), null));
            }
        });
    }

    /** Gọi API Đăng xuất thiết bị hiện tại (/logout) */
    public void logout(String refreshToken, MutableLiveData<Resource<Void>> result) {
        apiService.logout(new RefreshTokenRequest(refreshToken)).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful() || response.code() == 204) {
                    result.setValue(Resource.success(null));
                } else {
                    result.setValue(Resource.error(ErrorMapper.getHttpErrorMessage(appContext, response), null));
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                result.setValue(Resource.error(ErrorMapper.getErrorMessage(appContext, t), null));
            }
        });
    }
}
