package com.example.fintrack.app.user.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.example.fintrack.app.core.network.ApiClient;
import com.example.fintrack.app.core.network.ApiResponse;
import com.example.fintrack.app.core.network.ErrorMapper;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.user.remote.UpdateProfileRequest;
import com.example.fintrack.app.user.remote.UserApiService;
import com.example.fintrack.app.user.remote.UserProfileResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository chịu trách nhiệm xử lý các cuộc gọi API mạng (lấy và cập nhật thông tin cá nhân)
 * và chuyển đổi kết quả về dạng LiveData Resource cho ViewModel.
 */
public class UserRepository {

    private final Context appContext;
    private final UserApiService apiService;

    public UserRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.apiService = ApiClient.createService(appContext, UserApiService.class);
    }

    /** Gọi API lấy thông tin chi tiết hồ sơ cá nhân */
    public void getProfile(MutableLiveData<Resource<UserProfileResponse>> result) {
        apiService.getProfile().enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<UserProfileResponse>> call, @NonNull Response<ApiResponse<UserProfileResponse>> response) {
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
            public void onFailure(@NonNull Call<ApiResponse<UserProfileResponse>> call, @NonNull Throwable t) {
                result.setValue(Resource.error(ErrorMapper.getErrorMessage(appContext, t), null));
            }
        });
    }

    /** Gọi API cập nhật thông tin họ tên và số điện thoại */
    public void updateProfile(String name, String phone, MutableLiveData<Resource<UserProfileResponse>> result) {
        apiService.updateProfile(new UpdateProfileRequest(name, phone)).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<UserProfileResponse>> call, @NonNull Response<ApiResponse<UserProfileResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<UserProfileResponse> apiResponse = response.body();
                    if (apiResponse.isSuccess() && apiResponse.getData() != null) {
                        result.setValue(Resource.success(apiResponse.getData()));
                    } else {
                        String msg = apiResponse.getMessage() != null ? apiResponse.getMessage() : "Cập nhật profile thất bại";
                        result.setValue(Resource.error(msg, null));
                    }
                } else {
                    result.setValue(Resource.error(ErrorMapper.getHttpErrorMessage(appContext, response), null));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<UserProfileResponse>> call, @NonNull Throwable t) {
                result.setValue(Resource.error(ErrorMapper.getErrorMessage(appContext, t), null));
            }
        });
    }
}
