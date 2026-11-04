package com.example.fintrack.app.category.remote;

import com.example.fintrack.app.finance.domain.FinanceBook;
import com.google.gson.annotations.SerializedName;

/** Request DTO đóng gói dữ liệu khi Thêm Danh mục mới gửi lên Server */
public class CategoryRequest {

    @SerializedName("name")
    public final String name;

    @SerializedName("categoryType")
    public final FinanceBook.Type categoryType;

    public CategoryRequest(String name, FinanceBook.Type type) {
        this.name = name;
        categoryType = type;
    }
}
