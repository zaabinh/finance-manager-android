package com.example.fintrack.app.user.remote;

import com.example.fintrack.app.core.network.ApiResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PUT;

/**
 * Interface định nghĩa các API endpoint liên quan đến Hồ sơ người dùng (User Profile)
 * sử dụng thư viện Retrofit.
 */
public interface UserApiService {

    /** API Lấy thông tin hồ sơ cá nhân của người dùng hiện tại */
    @GET("api/v1/user/profile")
    Call<ApiResponse<UserProfileResponse>> getProfile();

    /** API Cập nhật thông tin họ tên và số điện thoại của người dùng */
    @PUT("api/v1/user/profile")
    Call<ApiResponse<UserProfileResponse>> updateProfile(@Body UpdateProfileRequest request);

    /** API Xóa vĩnh viễn tài khoản người dùng */
    @DELETE("api/v1/user/profile")
    Call<ApiResponse<Void>> deleteAccount();
}
