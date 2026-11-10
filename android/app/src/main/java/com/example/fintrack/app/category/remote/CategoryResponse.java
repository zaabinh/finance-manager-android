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

    public String iconKey, colorHex, archivedAt, createdAt, updatedAt;
    public int sortOrder;

    /** Chuyển đổi Response DTO sang mô hình dữ liệu Domain */
    public FinanceBook.Category domain() {
        FinanceBook.Category value = new FinanceBook.Category(categoryId, name, categoryType);
        value.iconKey = iconKey;
        value.colorHex = colorHex;
        value.archivedAt = archivedAt;
        value.sortOrder = sortOrder;
        return value;
    }
}
