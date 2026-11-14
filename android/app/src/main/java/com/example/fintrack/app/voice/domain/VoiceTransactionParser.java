package com.example.fintrack.app.voice.domain;

import com.example.fintrack.app.finance.domain.TransactionType;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trình phân tích văn bản giọng nói tiếng Việt thành thông tin giao dịch tài chính có cấu trúc.
 */
public final class VoiceTransactionParser {

    public static class ParsedResult {
        public final TransactionType type;
        public final long amount;
        public final String note;
        public final String rawTranscript;

        public ParsedResult(TransactionType type, long amount, String note, String rawTranscript) {
            this.type = type;
            this.amount = amount;
            this.note = note != null ? note : "";
            this.rawTranscript = rawTranscript != null ? rawTranscript : "";
        }
    }

    private VoiceTransactionParser() {}

    public static ParsedResult parse(String speech) {
        if (speech == null || speech.trim().isEmpty()) {
            return new ParsedResult(TransactionType.EXPENSE, 0, "", "");
        }

        String raw = speech.trim();
        String lower = raw.toLowerCase(Locale.ROOT);

        // 1. Xác định Loại giao dịch (INCOME vs EXPENSE)
        TransactionType type = TransactionType.EXPENSE;
        if (lower.contains("thu ") || lower.startsWith("thu") ||
            lower.contains("lương") || lower.contains("thưởng") ||
            lower.contains("được cho") || lower.contains("nhận ") ||
            lower.contains("tiền vào") || lower.contains("lãi ") ||
            lower.contains("bán ")) {
            type = TransactionType.INCOME;
        }

        // 2. Trích xuất Số tiền
        long amount = 0;
        String amountMatchStr = "";

        // Trường hợp 1: Số + "triệu" / "tr" / "củ" (vd: 15 triệu, 1.5 triệu, 2tr)
        Pattern pTrieu = Pattern.compile("(\\d+([.,]\\d+)?)\\s*(triệu|trieu|tr|củ|cu)\\b");
        Matcher mTrieu = pTrieu.matcher(lower);
        if (mTrieu.find()) {
            try {
                String numStr = mTrieu.group(1).replace(",", ".");
                double val = Double.parseDouble(numStr);
                amount = (long) (val * 1_000_000L);
                amountMatchStr = mTrieu.group(0);
            } catch (Exception ignored) {}
        }

        // Trường hợp 2: Số + "nghìn" / "ngàn" / "k" (vd: 45 nghìn, 50k, 120 ngàn)
        if (amount == 0) {
            Pattern pNghin = Pattern.compile("(\\d+([.,]\\d+)?)\\s*(nghìn|nghin|ngàn|ngan|k)\\b");
            Matcher mNghin = pNghin.matcher(lower);
            if (mNghin.find()) {
                try {
                    String numStr = mNghin.group(1).replace(",", ".");
                    double val = Double.parseDouble(numStr);
                    amount = (long) (val * 1_000L);
                    amountMatchStr = mNghin.group(0);
                } catch (Exception ignored) {}
            }
        }

        // Trường hợp 3: Số nguyên hoặc số có phân cách hàng nghìn (vd: 50.000, 150000)
        if (amount == 0) {
            Pattern pDigits = Pattern.compile("\\b(\\d{1,3}([.,]\\d{3})+|\\d{4,})\\b");
            Matcher mDigits = pDigits.matcher(lower);
            if (mDigits.find()) {
                try {
                    String numStr = mDigits.group(0).replaceAll("[.,]", "");
                    amount = Long.parseLong(numStr);
                    amountMatchStr = mDigits.group(0);
                } catch (Exception ignored) {}
            }
        }

        // Trường hợp 4: Số dạng chữ cơ bản
        if (amount == 0) {
            if (lower.contains("một triệu") || lower.contains("1 triệu")) { amount = 1_000_000L; amountMatchStr = "một triệu"; }
            else if (lower.contains("hai triệu") || lower.contains("2 triệu")) { amount = 2_000_000L; amountMatchStr = "hai triệu"; }
            else if (lower.contains("ba triệu") || lower.contains("3 triệu")) { amount = 3_000_000L; amountMatchStr = "ba triệu"; }
            else if (lower.contains("năm triệu") || lower.contains("5 triệu")) { amount = 5_000_000L; amountMatchStr = "năm triệu"; }
            else if (lower.contains("mười triệu") || lower.contains("10 triệu")) { amount = 10_000_000L; amountMatchStr = "mười triệu"; }
            else if (lower.contains("một trăm nghìn") || lower.contains("100 nghìn") || lower.contains("100k")) { amount = 100_000L; amountMatchStr = "một trăm nghìn"; }
            else if (lower.contains("hai trăm nghìn") || lower.contains("200 nghìn") || lower.contains("200k")) { amount = 200_000L; amountMatchStr = "hai trăm nghìn"; }
            else if (lower.contains("năm mươi nghìn") || lower.contains("50 nghìn") || lower.contains("50k")) { amount = 50_000L; amountMatchStr = "năm mươi nghìn"; }
        }

        // 3. Trích xuất Ghi chú / Nội dung chi tiêu
        String note = raw;
        if (!amountMatchStr.isEmpty()) {
            note = note.replaceAll("(?i)" + Pattern.quote(amountMatchStr), "");
        }
        // Loại bỏ các từ thừa phổ biến
        note = note.replaceAll("(?i)\\b(chi|thu|hết|mất|tiền|khoản|cho|vào|đã)\\b", " ");
        note = note.trim().replaceAll("\\s+", " ");

        if (note.isEmpty()) {
            note = type == TransactionType.INCOME ? "Thu nhập" : "Chi tiêu";
        } else {
            // Viết hoa chữ cái đầu
            note = Character.toUpperCase(note.charAt(0)) + (note.length() > 1 ? note.substring(1) : "");
        }

        return new ParsedResult(type, amount, note, raw);
    }
}
