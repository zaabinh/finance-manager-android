package com.example.fintrack.app.core.ui;

import java.util.ArrayList;
import java.util.List;

/** Pure money-text helpers: digits-only grouping with "." and amount suggestions. */
public final class MoneyFormat {
    public static final int MAX_DIGITS = 12;
    public static final long MAX_AMOUNT = 999_999_999_999L;
    private static final long[] MULTIPLIERS = {1_000L, 10_000L, 100_000L};
    private MoneyFormat() { }

    /** Keeps digits only, drops leading zeros (except a lone 0) and caps at {@link #MAX_DIGITS}. */
    public static String digits(CharSequence text) {
        StringBuilder out = new StringBuilder();
        if (text != null) {
            for (int i = 0; i < text.length() && out.length() < MAX_DIGITS; i++) {
                char c = text.charAt(i);
                if (c < '0' || c > '9') continue;
                if (out.length() == 1 && out.charAt(0) == '0') out.setLength(0);
                out.append(c);
            }
        }
        return out.toString();
    }

    /** "1000000" or "1000000abc" -> "1.000.000". */
    public static String group(CharSequence text) {
        String digits = digits(text);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) out.append('.');
            out.append(digits.charAt(i));
        }
        return out.toString();
    }

    public static String group(long value) { return group(Long.toString(value)); }

    /** Number of digits among the first {@code length} chars of text. */
    public static int digitsBefore(CharSequence text, int length) {
        int count = 0;
        for (int i = 0; i < Math.min(length, text.length()); i++) if (Character.isDigit(text.charAt(i))) count++;
        return count;
    }

    /** Index in grouped text just after the given number of digits. */
    public static int indexAfterDigits(CharSequence grouped, int digits) {
        if (digits <= 0) return 0;
        int count = 0;
        for (int i = 0; i < grouped.length(); i++) {
            if (Character.isDigit(grouped.charAt(i)) && ++count == digits) return i + 1;
        }
        return grouped.length();
    }

    /** Typed digits x1.000 / x10.000 / x100.000, skipping values above the maximum. Empty for blank or zero. */
    public static List<Long> suggestions(CharSequence text) {
        List<Long> out = new ArrayList<>();
        String digits = digits(text);
        if (digits.isEmpty()) return out;
        long base = Long.parseLong(digits);
        if (base <= 0) return out;
        for (long multiplier : MULTIPLIERS) {
            if (base > MAX_AMOUNT / multiplier) break;
            out.add(base * multiplier);
        }
        return out;
    }
}
