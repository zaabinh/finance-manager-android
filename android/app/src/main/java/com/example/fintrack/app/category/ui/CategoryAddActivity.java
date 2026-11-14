package com.example.fintrack.app.category.ui;

public class CategoryAddActivity extends CategoryFormActivity {
    /** TransactionType name (INCOME or EXPENSE) preselected in the form. */
    public static final String EXTRA_CATEGORY_TYPE = "category_type";
    @Override protected boolean editing() { return false; }
}
