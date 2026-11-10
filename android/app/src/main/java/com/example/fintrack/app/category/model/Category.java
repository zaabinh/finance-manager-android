package com.example.fintrack.app.category.model;

public final class Category {
    public enum Type { EXPENSE, INCOME, TRANSFER }

    public String id, name;
    public Type type;

    public Category(String id, String name, Type type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }
}
