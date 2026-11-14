package com.example.fintrack.app.category.model;

import com.example.fintrack.app.finance.domain.TransactionType;

import java.util.Locale;

/**
 * Colour selection for the category forms: follows the type default (red expense / green income)
 * until the user picks a swatch (or an existing category brings its stored colour); then that choice sticks.
 */
public final class CategoryColorChoice {
    private TransactionType type;
    private String manual; // upper-case #RRGGBB, or null while following the type default

    public CategoryColorChoice(TransactionType type, String storedHex) {
        this.type = type;
        this.manual = valid(storedHex) ? storedHex.toUpperCase(Locale.ROOT) : null;
    }

    public void setType(TransactionType type) { this.type = type; }
    public void pick(String hex) { if (valid(hex)) manual = hex.toUpperCase(Locale.ROOT); }
    /** The colour to display and save. */
    public String selected() { return manual != null ? manual : CategoryIcons.defaultColor(type); }
    /** The picked colour (null = still following the type); persisted across rotation. */
    public String manual() { return manual; }

    private static boolean valid(String hex) { return hex != null && hex.matches("#[0-9a-fA-F]{6}"); }
}
