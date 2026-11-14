package com.example.fintrack.app.core.ui;

import com.example.fintrack.app.finance.domain.Category;
import com.example.fintrack.app.finance.domain.TransactionType;
import com.example.fintrack.app.finance.domain.Wallet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class PickerSheetTest {
    @Test public void walletsSkipArchivedAndExcluded() {
        Wallet a = new Wallet("a", "A", 0), b = new Wallet("b", "B", 0),
                c = new Wallet("c", "C", 0).withArchivedAt("2026-01-01T00:00:00Z");
        List<PickerSheet.Option> options = PickerSheet.wallets(Arrays.asList(a, b, c), "a");
        assertEquals(1, options.size());
        assertEquals("b", options.get(0).id);
    }
    @Test public void categoriesMatchTypeAndActive() {
        Category x = new Category("x", "X", TransactionType.EXPENSE),
                i = new Category("i", "I", TransactionType.INCOME),
                old = new Category("o", "O", TransactionType.EXPENSE).withArchivedAt("2026-01-01T00:00:00Z");
        List<PickerSheet.Option> options = PickerSheet.categories(Arrays.asList(x, i, old), TransactionType.EXPENSE);
        assertEquals(1, options.size());
        assertEquals("x", options.get(0).id);
    }
    @Test public void selectionOnlyIfOffered() {
        List<PickerSheet.Option> options = PickerSheet.wallets(Arrays.asList(new Wallet("a", "A", 0)), null);
        assertEquals("a", PickerSheet.initialSelection(options, "a"));
        assertNull(PickerSheet.initialSelection(options, "gone"));
        assertNull(PickerSheet.initialSelection(options, null));
    }
}
