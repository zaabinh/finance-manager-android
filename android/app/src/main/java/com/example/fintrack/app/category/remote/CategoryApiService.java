package com.example.fintrack.app.category.remote;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.*;

/**
 * Interface định nghĩa các API endpoint liên quan đến Danh mục thu/chi (Categories)
 * sử dụng thư viện Retrofit.
 */
public interface CategoryApiService {

    /** API Lấy danh sách toàn bộ danh mục của người dùng */
    @GET("api/v1/categories")
    Call<List<CategoryResponse>> categories();

    /** API Thêm mới một danh mục thu/chi */
    @POST("api/v1/categories")
    Call<CategoryResponse> addCategory(@Body CategoryRequest request);
}
