package com.example.fintrack.app.transaction.domain;

import com.example.fintrack.app.finance.domain.FinanceBook;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative suggestions: ambiguous totals stay blank and always require human confirmation. */
public final class ReceiptParser {
    public static final class Draft {
        public final long amount;
        public final String payee;
        public Draft(long amount, String payee) { this.amount = amount; this.payee = payee; }
    }
    public static Draft parse(String raw) {
        String[] lines = raw == null ? new String[0] : raw.split("\\r?\\n");
        String payee = "";
        Set<Long> candidates = new LinkedHashSet<>();
        Pattern number = Pattern.compile("(?<![0-9.,])(?:[0-9]{1,3}(?:[.,][0-9]{3})+|[0-9]+)(?![0-9.,])");
        for (String line : lines) {
            if (payee.isEmpty() && !line.trim().isEmpty()) payee = line.trim().substring(0, Math.min(160, line.trim().length()));
            String folded = Normalizer.normalize(line.toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            if (!(folded.contains("tong cong") || folded.contains("tong tien") || folded.contains("thanh toan")
                    || folded.matches(".*\\btotal\\b.*"))) continue;
            if (folded.contains("sub") || folded.contains("tam tinh") || folded.contains("khach dua") || folded.contains("tien thua")) continue;
            Matcher matcher = number.matcher(line);
            Long value = null;
            int numbers = 0;
            while (matcher.find()) {
                numbers++;
                try { value = FinanceBook.parseAmount(matcher.group().replace(',', '.')); }
                catch (FinanceBook.Failure ignored) { value = null; }
            }
            if (value != null && numbers == 1) candidates.add(value);
        }
        return new Draft(candidates.size() == 1 ? candidates.iterator().next() : 0, payee);
    }
    private ReceiptParser() { }
}
