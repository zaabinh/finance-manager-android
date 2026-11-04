package com.example.fintrack.app.user.repository;

import android.content.Context;

import androidx.lifecycle.MutableLiveData;

import com.example.fintrack.app.core.network.ApiClient;
import com.example.fintrack.app.core.network.ApiResponse;
import com.example.fintrack.app.core.network.ErrorMapper;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.user.remote.UserApiService;
import com.example.fintrack.app.user.remote.UserProfileResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserRepository {

    private final Context appContext;
    private final UserApiService apiService;

    public UserRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.apiService = ApiClient.createService(appContext, UserApiService.class);
    }

    public void getProfile(MutableLiveData<Resource<UserProfileResponse>> result) {
        apiService.getProfile().enqueue(new Callback<ApiResponse<UserProfileResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<UserProfileResponse>> call, Response<ApiResponse<UserProfileResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<UserProfileResponse> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        result.setValue(Resource.success(apiResponse.getData()));
                    } else {
                        String msg = apiResponse.getMessage() != null ? apiResponse.getMessage() : "Không thể lấy thông tin profile";
                        result.setValue(Resource.error(msg, null));
                    }
                } else {
                    result.setValue(Resource.error(ErrorMapper.getHttpErrorMessage(appContext, response), null));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<UserProfileResponse>> call, Throwable t) {
                result.setValue(Resource.error(ErrorMapper.getErrorMessage(appContext, t), null));
            }
        });
    }
}
