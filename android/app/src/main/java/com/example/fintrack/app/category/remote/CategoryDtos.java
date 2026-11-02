package com.example.fintrack.app.category.remote;

import com.example.fintrack.app.finance.domain.FinanceBook;

public final class CategoryDtos {

    public static final class Category {
        public String categoryId, name;
        public FinanceBook.Type categoryType;

        public FinanceBook.Category domain() {
            return new FinanceBook.Category(categoryId, name, categoryType);
        }
    }

    public static final class CategoryRequest {
        public final String name;
        public final FinanceBook.Type categoryType;

        public CategoryRequest(String name, FinanceBook.Type type) {
            this.name = name;
            categoryType = type;
        }
    }

    private CategoryDtos() { }
}
