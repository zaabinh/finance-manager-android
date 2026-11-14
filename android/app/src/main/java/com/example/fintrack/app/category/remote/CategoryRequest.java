package com.example.fintrack.app.category.remote;

import com.example.fintrack.app.finance.domain.TransactionType;

import com.google.gson.annotations.SerializedName;

/** Request DTO đóng gói dữ liệu khi Thêm Danh mục mới gửi lên Server */
public class CategoryRequest {

    @SerializedName("name")
    public final String name;

    @SerializedName("categoryType")
    public final TransactionType categoryType;

    public final String iconKey;
    public final String colorHex;
    public final int sortOrder;

    public CategoryRequest(String name, TransactionType type) {
        this(name, type, null, null, 0);
    }

    public CategoryRequest(String name, TransactionType type, String iconKey, String colorHex, int sortOrder) {
        this.name = name;
        categoryType = type;
        this.iconKey = iconKey;
        this.colorHex = colorHex;
        this.sortOrder = sortOrder;
    }
}
