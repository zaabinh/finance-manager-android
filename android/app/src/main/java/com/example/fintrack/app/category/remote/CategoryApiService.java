package com.example.fintrack.app.category.remote;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.*;

public interface CategoryApiService {

    @GET("api/v1/categories")
    Call<List<CategoryDtos.Category>> categories();

    @POST("api/v1/categories")
    Call<CategoryDtos.Category> addCategory(@Body CategoryDtos.CategoryRequest request);
}
