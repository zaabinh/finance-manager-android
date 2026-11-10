package com.example.fintrack.app.core.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.fintrack.app.finance.domain.FinanceBook;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class PickerSheetTest {
    @Test public void walletsSkipArchivedAndExcluded() {
        FinanceBook.Wallet a = new FinanceBook.Wallet("a", "A", 0), b = new FinanceBook.Wallet("b", "B", 0),
                c = new FinanceBook.Wallet("c", "C", 0);
        c.archivedAt = "2026-01-01T00:00:00Z";
        List<PickerSheet.Option> options = PickerSheet.wallets(Arrays.asList(a, b, c), "a");
        assertEquals(1, options.size());
        assertEquals("b", options.get(0).id);
    }
    @Test public void categoriesMatchTypeAndActive() {
        FinanceBook.Category x = new FinanceBook.Category("x", "X", FinanceBook.Type.EXPENSE),
                i = new FinanceBook.Category("i", "I", FinanceBook.Type.INCOME),
                old = new FinanceBook.Category("o", "O", FinanceBook.Type.EXPENSE);
        old.archivedAt = "2026-01-01T00:00:00Z";
        List<PickerSheet.Option> options = PickerSheet.categories(Arrays.asList(x, i, old), FinanceBook.Type.EXPENSE);
        assertEquals(1, options.size());
        assertEquals("x", options.get(0).id);
    }
    @Test public void selectionOnlyIfOffered() {
        List<PickerSheet.Option> options = PickerSheet.wallets(Arrays.asList(new FinanceBook.Wallet("a", "A", 0)), null);
        assertEquals("a", PickerSheet.initialSelection(options, "a"));
        assertNull(PickerSheet.initialSelection(options, "gone"));
        assertNull(PickerSheet.initialSelection(options, null));
    }
}
