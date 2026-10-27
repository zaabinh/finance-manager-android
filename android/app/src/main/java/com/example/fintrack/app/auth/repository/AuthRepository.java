package com.example.fintrack.app.auth.repository;

import android.content.Context;
import android.text.TextUtils;

import androidx.lifecycle.MutableLiveData;

import com.example.fintrack.R;
import com.example.fintrack.app.auth.remote.AuthApiService;
import com.example.fintrack.app.auth.remote.ForgotPasswordRequest;
import com.example.fintrack.app.auth.remote.LoginRequest;
import com.example.fintrack.app.auth.remote.LoginResponse;
import com.example.fintrack.app.auth.remote.RegisterRequest;
import com.example.fintrack.app.auth.remote.ResendOtpRequest;
import com.example.fintrack.app.auth.remote.ResetPasswordRequest;
import com.example.fintrack.app.auth.remote.VerifyEmailRequest;
import com.example.fintrack.app.core.network.ApiClient;
import com.example.fintrack.app.core.network.ApiResponse;
import com.example.fintrack.app.core.network.ErrorMapper;
import com.example.fintrack.app.core.network.Resource;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthRepository {
    private final Context appContext;
    private final AuthApiService apiService;

    public AuthRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.apiService = ApiClient.createService(appContext, AuthApiService.class);
    }

    public void login(String usernameOrEmail, String password, MutableLiveData<Resource<LoginResponse>> result) {
        apiService.login(new LoginRequest(usernameOrEmail, password)).enqueue(new Callback<ApiResponse<LoginResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<LoginResponse>> call, Response<ApiResponse<LoginResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<LoginResponse> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null && !TextUtils.isEmpty(apiResponse.getData().getAccessToken())) {
                        result.setValue(Resource.success(apiResponse.getData()));
                    } else {
                        String msg = !TextUtils.isEmpty(apiResponse.getMessage()) ? apiResponse.getMessage() : appContext.getString(R.string.login_response_invalid);
                        result.setValue(Resource.error(msg, null));
                    }
                } else {
                    result.setValue(Resource.error(ErrorMapper.getHttpErrorMessage(appContext, response), null));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<LoginResponse>> call, Throwable t) {
                result.setValue(Resource.error(ErrorMapper.getErrorMessage(appContext, t), null));
            }
        });
    }

    public void register(String username, String email, String password, MutableLiveData<Resource<Void>> result) {
        apiService.register(new RegisterRequest(username, email, password)).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Void> apiResponse = response.body();
                    if (apiResponse.isSuccess()) {
                        result.setValue(Resource.success(null));
                    } else {
                        String msg = !TextUtils.isEmpty(apiResponse.getMessage()) ? apiResponse.getMessage() : appContext.getString(R.string.register_failed);
                        result.setValue(Resource.error(msg, null));
                    }
                } else {
                    result.setValue(Resource.error(ErrorMapper.getHttpErrorMessage(appContext, response), null));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                result.setValue(Resource.error(ErrorMapper.getErrorMessage(appContext, t), null));
            }
        });
    }

    public void verifyEmail(String email, String otp, MutableLiveData<Resource<Void>> result) {
        apiService.verifyEmail(new VerifyEmailRequest(email, otp)).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Void> apiResponse = response.body();
                    if (apiResponse.isSuccess()) {
                        result.setValue(Resource.success(null));
                    } else {
                        String msg = !TextUtils.isEmpty(apiResponse.getMessage()) ? apiResponse.getMessage() : appContext.getString(R.string.verify_email_failed);
                        result.setValue(Resource.error(msg, null));
                    }
                } else {
                    result.setValue(Resource.error(ErrorMapper.getHttpErrorMessage(appContext, response), null));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                result.setValue(Resource.error(ErrorMapper.getErrorMessage(appContext, t), null));
            }
        });
    }

    public void resendOtp(String email, MutableLiveData<Resource<Void>> result) {
        apiService.resendOtp(new ResendOtpRequest(email)).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Void> apiResponse = response.body();
                    if (apiResponse.isSuccess()) {
                        result.setValue(Resource.success(null));
                    } else {
                        String msg = !TextUtils.isEmpty(apiResponse.getMessage()) ? apiResponse.getMessage() : appContext.getString(R.string.resend_otp_failed);
                        result.setValue(Resource.error(msg, null));
                    }
                } else {
                    result.setValue(Resource.error(ErrorMapper.getHttpErrorMessage(appContext, response), null));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                result.setValue(Resource.error(ErrorMapper.getErrorMessage(appContext, t), null));
            }
        });
    }

    public void forgotPassword(String email, MutableLiveData<Resource<Void>> result) {
        apiService.forgotPassword(new ForgotPasswordRequest(email)).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Void> apiResponse = response.body();
                    if (apiResponse.isSuccess()) {
                        result.setValue(Resource.success(null));
                    } else {
                        String msg = !TextUtils.isEmpty(apiResponse.getMessage()) ? apiResponse.getMessage() : appContext.getString(R.string.forgot_password_failed);
                        result.setValue(Resource.error(msg, null));
                    }
                } else {
                    result.setValue(Resource.error(ErrorMapper.getHttpErrorMessage(appContext, response), null));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                result.setValue(Resource.error(ErrorMapper.getErrorMessage(appContext, t), null));
            }
        });
    }

    public void resetPassword(String email, String otp, String newPassword, MutableLiveData<Resource<Void>> result) {
        apiService.resetPassword(new ResetPasswordRequest(email, otp, newPassword)).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Void> apiResponse = response.body();
                    if (apiResponse.isSuccess()) {
                        result.setValue(Resource.success(null));
                    } else {
                        String msg = !TextUtils.isEmpty(apiResponse.getMessage()) ? apiResponse.getMessage() : appContext.getString(R.string.reset_password_failed);
                        result.setValue(Resource.error(msg, null));
                    }
                } else {
                    result.setValue(Resource.error(ErrorMapper.getHttpErrorMessage(appContext, response), null));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                result.setValue(Resource.error(ErrorMapper.getErrorMessage(appContext, t), null));
            }
        });
    }
}
