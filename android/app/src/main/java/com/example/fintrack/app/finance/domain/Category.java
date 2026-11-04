package com.example.fintrack.app.finance.domain;

public final class Category {
    public String id, name;
    public FinanceBook.Type type;

    public Category(String id, String name, FinanceBook.Type type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }
}
