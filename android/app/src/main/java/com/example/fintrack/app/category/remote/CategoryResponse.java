package com.example.fintrack.app.category.remote;

import com.example.fintrack.app.finance.domain.Category;
import com.example.fintrack.app.finance.domain.TransactionType;

import com.google.gson.annotations.SerializedName;

/** Response DTO nhận dữ liệu Danh mục trả về từ Server */
public class CategoryResponse {

    @SerializedName("categoryId")
    public String categoryId;

    @SerializedName("name")
    public String name;

    @SerializedName("categoryType")
    public TransactionType categoryType;

    public String iconKey, colorHex, archivedAt, createdAt, updatedAt;
    public int sortOrder;

    /** Chuyển đổi Response DTO sang mô hình dữ liệu Domain */
    public Category domain() {
        return new Category(categoryId,name,categoryType,archivedAt,iconKey,colorHex,sortOrder);
    }
}
