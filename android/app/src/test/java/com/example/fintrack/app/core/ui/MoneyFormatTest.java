package com.example.fintrack.app.core.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class MoneyFormatTest {
    @Test public void groupsThousands() {
        assertEquals("", MoneyFormat.group(""));
        assertEquals("1", MoneyFormat.group("1"));
        assertEquals("1.000", MoneyFormat.group("1000"));
        assertEquals("1.000.000", MoneyFormat.group("1.000000"));
        assertEquals("95.000", MoneyFormat.group("95000"));
    }
    @Test public void stripsNonDigitsAndLeadingZeros() {
        assertEquals("1.234", MoneyFormat.group("a1,2 34"));
        assertEquals("5", MoneyFormat.group("0005"));
        assertEquals("0", MoneyFormat.group("000"));
    }
    @Test public void capsAtTwelveDigits() {
        assertEquals("999.999.999.999", MoneyFormat.group("9999999999999999"));
    }
    @Test public void cursorMapping() {
        assertEquals(3, MoneyFormat.digitsBefore("1.00", 4));
        assertEquals(5, MoneyFormat.indexAfterDigits("1.000.000", 4));
        assertEquals(0, MoneyFormat.indexAfterDigits("1.000", 0));
        assertEquals(5, MoneyFormat.indexAfterDigits("1.000", 99));
    }
    @Test public void suggestionsForOne() {
        assertEquals(Arrays.asList(1_000L, 10_000L, 100_000L), MoneyFormat.suggestions("1"));
    }
    @Test public void suggestionsSkipAboveMax() {
        assertEquals(Arrays.asList(900_000_000_000L), MoneyFormat.suggestions("900.000.000"));
        assertEquals(Arrays.asList(5_000_000_000L, 50_000_000_000L, 500_000_000_000L), MoneyFormat.suggestions("5.000.000"));
        assertTrue(MoneyFormat.suggestions("999.999.999.999").isEmpty());
    }
    @Test public void noSuggestionsWhenEmptyOrZero() {
        assertTrue(MoneyFormat.suggestions("").isEmpty());
        assertTrue(MoneyFormat.suggestions("0").isEmpty());
        assertTrue(MoneyFormat.suggestions(null).isEmpty());
    }
}
