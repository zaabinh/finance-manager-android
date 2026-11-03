package com.example.fintrack.app.category.remote;

import com.example.fintrack.app.finance.domain.FinanceBook;
import com.google.gson.annotations.SerializedName;

/** Response DTO nhận dữ liệu Danh mục trả về từ Server */
public class CategoryResponse {

    @SerializedName("categoryId")
    public String categoryId;

    @SerializedName("name")
    public String name;

    @SerializedName("categoryType")
    public FinanceBook.Type categoryType;

    /** Chuyển đổi Response DTO sang mô hình dữ liệu Domain */
    public FinanceBook.Category domain() {
        return new FinanceBook.Category(categoryId, name, categoryType);
    }
}
