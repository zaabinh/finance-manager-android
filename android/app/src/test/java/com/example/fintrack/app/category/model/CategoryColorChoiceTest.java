package com.example.fintrack.app.category.model;

import com.example.fintrack.app.finance.domain.TransactionType;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class CategoryColorChoiceTest {
    @Test public void followsTypeUntilUserPicks() {
        CategoryColorChoice choice = new CategoryColorChoice(TransactionType.EXPENSE, null);
        assertEquals(CategoryIcons.EXPENSE_COLOR, choice.selected());
        choice.setType(TransactionType.INCOME);
        assertEquals(CategoryIcons.INCOME_COLOR, choice.selected());
        choice.pick("#2e68b5");
        choice.setType(TransactionType.EXPENSE);
        assertEquals("#2E68B5", choice.selected());
    }
    @Test public void storedColourWinsAndInvalidIsIgnored() {
        assertEquals("#7A4FB3", new CategoryColorChoice(TransactionType.INCOME, "#7a4fb3").selected());
        CategoryColorChoice choice = new CategoryColorChoice(TransactionType.INCOME, "red");
        assertNull(choice.manual());
        choice.pick("nope");
        assertEquals(CategoryIcons.INCOME_COLOR, choice.selected());
    }
}
