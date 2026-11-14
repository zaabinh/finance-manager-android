package com.example.fintrack.app.finance.domain;

/** Immutable finance entity. */
public final class Category {
    public final String id;
    public final String name;
    public final TransactionType type;
    public final String archivedAt;
    public final String iconKey;
    public final String colorHex;
    public final int sortOrder;
    public Category(String id, String name, TransactionType type, String archivedAt, String iconKey, String colorHex, int sortOrder) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.archivedAt = archivedAt;
        this.iconKey = iconKey;
        this.colorHex = colorHex;
        this.sortOrder = sortOrder;
    }
    public Category(String id, String name, TransactionType type) { this(id, name, type, null, null, null, 0); }
    public Category withArchivedAt(String value) {
        return new Category(id, name, type, value, iconKey, colorHex, sortOrder);
    }
}
