package com.example.fintrack.app.category.model;

import static org.junit.Assert.*;

import com.example.fintrack.app.finance.domain.FinanceBook;
import org.junit.Test;

public class CategoryIconsTest {
    @Test public void defaultColourIsRedForExpenseAndGreenForIncome() {
        assertEquals(CategoryIcons.EXPENSE_COLOR, CategoryIcons.color(null, FinanceBook.Type.EXPENSE));
        assertEquals(CategoryIcons.INCOME_COLOR, CategoryIcons.color(null, FinanceBook.Type.INCOME));
        assertEquals(CategoryIcons.INCOME_COLOR, CategoryIcons.color("#fff", FinanceBook.Type.INCOME)); // invalid → default
        assertEquals("#2E68B5", CategoryIcons.color("#2E68B5", FinanceBook.Type.EXPENSE));
        assertEquals(CategoryIcons.EXPENSE_COLOR, CategoryIcons.COLORS.get(0));
        assertEquals(CategoryIcons.INCOME_COLOR, CategoryIcons.COLORS.get(1));
    }

    @Test public void everyKeyRoundTripsAndMockSeedsUseCatalogueIcons() {
        assertEquals(CategoryIcons.KEYS.size(), CategoryIcons.RESOURCES.size());
        for (String key : CategoryIcons.KEYS) assertEquals(key, CategoryIcons.key(CategoryIcons.resource(key)));
        for (FinanceBook.Category category : FinanceBook.empty().categories)
            assertTrue(category.name + " uses " + category.iconKey, CategoryIcons.KEYS.contains(category.iconKey));
    }
}
