package com.example.fintrack.app.voice.domain;

import com.example.fintrack.app.finance.domain.TransactionType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trình phân tích văn bản giọng nói tiếng Việt thành thông tin giao dịch tài chính có cấu trúc.
 * Hỗ trợ nhận diện số tiền theo số, chữ, từ lóng (củ, lít, chục, rưỡi), loại giao dịch (Chi tiêu, Thu nhập, Chuyển khoản)
 * và gợi ý ví đích khi chuyển khoản.
 */
public final class VoiceTransactionParser {

    public static class ParsedResult {
        public final TransactionType type;
        public final long amount;
        public final String note;
        public final String rawTranscript;
        public final String destinationWalletHint;
        public final String sourceWalletHint;
        public final long occurredAt;

        public ParsedResult(TransactionType type, long amount, String note, String rawTranscript,
                            String destinationWalletHint, String sourceWalletHint, long occurredAt) {
            this.type = type;
            this.amount = amount;
            this.note = note != null ? note : "";
            this.rawTranscript = rawTranscript != null ? rawTranscript : "";
            this.destinationWalletHint = destinationWalletHint != null ? destinationWalletHint : "";
            this.sourceWalletHint = sourceWalletHint != null ? sourceWalletHint : "";
            this.occurredAt = occurredAt > 0 ? occurredAt : System.currentTimeMillis();
        }

        public ParsedResult(TransactionType type, long amount, String note, String rawTranscript, String destinationWalletHint) {
            this(type, amount, note, rawTranscript, destinationWalletHint, "", System.currentTimeMillis());
        }

        public ParsedResult(TransactionType type, long amount, String note, String rawTranscript) {
            this(type, amount, note, rawTranscript, "", "", System.currentTimeMillis());
        }
    }

    private static final Map<String, Integer> WORD_DIGITS = new HashMap<>();
    static {
        WORD_DIGITS.put("không", 0);
        WORD_DIGITS.put("khong", 0);
        WORD_DIGITS.put("một", 1);
        WORD_DIGITS.put("mot", 1);
        WORD_DIGITS.put("mốt", 1);
        WORD_DIGITS.put("hai", 2);
        WORD_DIGITS.put("ba", 3);
        WORD_DIGITS.put("bốn", 4);
        WORD_DIGITS.put("bon", 4);
        WORD_DIGITS.put("tư", 4);
        WORD_DIGITS.put("tu", 4);
        WORD_DIGITS.put("năm", 5);
        WORD_DIGITS.put("nam", 5);
        WORD_DIGITS.put("lăm", 5);
        WORD_DIGITS.put("lam", 5);
        WORD_DIGITS.put("sáu", 6);
        WORD_DIGITS.put("sau", 6);
        WORD_DIGITS.put("bảy", 7);
        WORD_DIGITS.put("bay", 7);
        WORD_DIGITS.put("bẩy", 7);
        WORD_DIGITS.put("tám", 8);
        WORD_DIGITS.put("tam", 8);
        WORD_DIGITS.put("chín", 9);
        WORD_DIGITS.put("chin", 9);
        WORD_DIGITS.put("mười", 10);
        WORD_DIGITS.put("muoi", 10);
    }

    private VoiceTransactionParser() {}

    public static ParsedResult parse(String speech) {
        if (speech == null || speech.trim().isEmpty()) {
            return new ParsedResult(TransactionType.EXPENSE, 0, "", "");
        }

        String raw = speech.trim();
        String lower = raw.toLowerCase(Locale.ROOT);

        // 1. Xác định Loại giao dịch (TRANSFER, INCOME, EXPENSE)
        TransactionType type = TransactionType.EXPENSE;
        if (lower.contains("chuyển khoản") || lower.contains("chuyển tiền") ||
            lower.contains("chuyển sang") || lower.contains("chuyển vào") ||
            lower.contains("chuyển cho") || lower.contains("bắn tiền") ||
            lower.contains("bắn sang") || lower.contains("bắn cho") ||
            lower.startsWith("chuyển") || lower.startsWith("bắn ")) {
            type = TransactionType.TRANSFER;
        } else if (lower.contains("thu ") || lower.startsWith("thu") ||
            lower.contains("lương") || lower.contains("thưởng") ||
            lower.contains("được cho") || lower.contains("được tặng") ||
            lower.contains("được lì xì") || lower.contains("nhận ") ||
            lower.contains("tiền vào") || lower.contains("lãi ") ||
            lower.contains("bán ") || lower.contains("hoàn tiền") ||
            lower.contains("lì xì") || lower.contains("cộng tiền") ||
            lower.contains("thu nhập") || lower.contains("thu nợ")) {
            type = TransactionType.INCOME;
        }

        // 2. Trích xuất Số tiền
        long amount = 0;
        String amountMatchStr = "";

        // Trường hợp 1: Tỷ (1.5 tỷ, 2 tỷ)
        Pattern pTy = Pattern.compile("(\\d+([.,]\\d+)?)\\s*(tỷ|ty)(?![\\p{L}\\d])");
        Matcher mTy = pTy.matcher(lower);
        if (mTy.find()) {
            try {
                String numStr = mTy.group(1).replace(",", ".");
                double val = Double.parseDouble(numStr);
                amount = (long) (val * 1_000_000_000L);
                amountMatchStr = mTy.group(0);
            } catch (Exception ignored) {}
        }

        // Trường hợp 2: Triệu / Tr / Củ (15 triệu, 2.5 triệu, 2 triệu rưỡi, 2 củ rưỡi)
        if (amount == 0) {
            Pattern pTrieu = Pattern.compile("(\\d+([.,]\\d+)?)\\s*(triệu|trieu|tr|củ|cu)(\\s*(rưỡi|ruoi))?(?![\\p{L}\\d])");
            Matcher mTrieu = pTrieu.matcher(lower);
            if (mTrieu.find()) {
                try {
                    String numStr = mTrieu.group(1).replace(",", ".");
                    double val = Double.parseDouble(numStr);
                    amount = (long) (val * 1_000_000L);
                    if (mTrieu.group(4) != null) {
                        amount += 500_000L;
                    }
                    amountMatchStr = mTrieu.group(0);
                } catch (Exception ignored) {}
            }
        }

        // Trường hợp 3: Nghìn / Ngàn / K (45 nghìn, 50k, 120 ngàn, 1.5k)
        if (amount == 0) {
            Pattern pNghin = Pattern.compile("(\\d+([.,]\\d+)?)\\s*(nghìn|nghin|ngàn|ngan|k)(?![\\p{L}\\d])");
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

        // Trường hợp 4: Trăm (1 trăm, 2 trăm rưỡi = 250k, 5 trăm = 500k)
        if (amount == 0) {
            Pattern pTram = Pattern.compile("(\\d+)\\s*(trăm|tram)(\\s*(rưỡi|ruoi))?(?![\\p{L}\\d])");
            Matcher mTram = pTram.matcher(lower);
            if (mTram.find()) {
                try {
                    long val = Long.parseLong(mTram.group(1));
                    amount = val * 100_000L + (mTram.group(3) != null ? 50_000L : 0);
                    amountMatchStr = mTram.group(0);
                } catch (Exception ignored) {}
            }
        }

        // Trường hợp 5: Lít (1 lít = 100k, 2 lít = 200k, 5 lít = 500k)
        if (amount == 0) {
            Pattern pLit = Pattern.compile("(\\d+)\\s*(lít|lit)(?![\\p{L}\\d])");
            Matcher mLit = pLit.matcher(lower);
            if (mLit.find()) {
                try {
                    long val = Long.parseLong(mLit.group(1));
                    amount = val * 100_000L;
                    amountMatchStr = mLit.group(0);
                } catch (Exception ignored) {}
            }
        }

        // Trường hợp 6: Chục (5 chục = 50k, 2 chục = 20k)
        if (amount == 0) {
            Pattern pChuc = Pattern.compile("(\\d+)\\s*(chục|chuc)(\\s*(nghìn|ngàn|k))?(?![\\p{L}\\d])");
            Matcher mChuc = pChuc.matcher(lower);
            if (mChuc.find()) {
                try {
                    long val = Long.parseLong(mChuc.group(1));
                    amount = val * 10_000L;
                    amountMatchStr = mChuc.group(0);
                } catch (Exception ignored) {}
            }
        }

        // Trường hợp 7: Tiếng lóng không kèm số phía trước
        if (amount == 0) {
            if (lower.contains("trăm rưỡi") || lower.contains("tram ruoi")) {
                amount = 150_000L;
                amountMatchStr = lower.contains("trăm rưỡi") ? "trăm rưỡi" : "tram ruoi";
            } else if (lower.contains("nửa triệu") || lower.contains("nua trieu")) {
                amount = 500_000L;
                amountMatchStr = lower.contains("nửa triệu") ? "nửa triệu" : "nua trieu";
            } else if (lower.contains("nửa củ") || lower.contains("nua cu")) {
                amount = 500_000L;
                amountMatchStr = lower.contains("nửa củ") ? "nửa củ" : "nua cu";
            } else if (lower.contains("nửa lít") || lower.contains("nua lit")) {
                amount = 50_000L;
                amountMatchStr = lower.contains("nửa lít") ? "nửa lít" : "nua lit";
            }
        }

        // Trường hợp 8: Chữ số chuẩn có định dạng hoặc số liền (vd: 50.000, 150000)
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

        // Trường hợp 9: Số dạng chữ tiếng Việt đầy đủ
        if (amount == 0) {
            amountMatchStr = parseWordsAmount(lower);
            if (!amountMatchStr.isEmpty()) {
                amount = calculateWordsAmount(amountMatchStr);
            }
        }

        // Nếu ngay sau cụm số tiền có đơn vị tiền tệ ("đồng", "vnd", "đ"), gộp vào để xóa sạch khỏi ghi chú
        if (!amountMatchStr.isEmpty()) {
            int idx = lower.indexOf(amountMatchStr.toLowerCase(Locale.ROOT));
            if (idx >= 0) {
                String after = lower.substring(idx + amountMatchStr.length());
                Pattern pCurrency = Pattern.compile("^\\s*(đồng|dong|vnd|vnđ|đ|d)(?![\\p{L}\\d])");
                Matcher mCurrency = pCurrency.matcher(after);
                if (mCurrency.find()) {
                    amountMatchStr = raw.substring(idx, idx + amountMatchStr.length() + mCurrency.end());
                }
            }
        }

        // 3. Trích xuất thời gian tự nhiên (hôm qua, hôm kia)
        long occurredAt = System.currentTimeMillis();
        if (lower.contains("hôm qua") || lower.contains("hom qua")) {
            occurredAt -= 86400000L;
        } else if (lower.contains("hôm kia") || lower.contains("hom kia")) {
            occurredAt -= 2 * 86400000L;
        }

        // 4. Trích xuất gợi ý phương thức thanh toán / ví nguồn
        String sourceWalletHint = "";
        if (lower.contains("quẹt thẻ") || lower.contains("thẻ tín dụng") || lower.contains("thẻ visa") ||
            lower.contains("visa") || lower.contains("mastercard") || lower.contains("bằng thẻ") || lower.contains("qua thẻ") ||
            lower.matches(".*(?<![\\p{L}\\d])thẻ(?![\\p{L}\\d]).*")) {
            sourceWalletHint = "thẻ";
        } else if (lower.contains("momo") || lower.contains("ví momo")) {
            sourceWalletHint = "momo";
        } else if (lower.contains("tiền mặt")) {
            sourceWalletHint = "tiền mặt";
        } else if (lower.contains("zalo") || lower.contains("zalopay")) {
            sourceWalletHint = "zalo";
        } else if (lower.contains("chuyển khoản") || lower.contains("banking") || lower.contains("vcb") ||
            lower.contains("vietcombank") || lower.contains("techcombank") || lower.contains("mb") || lower.contains("mbbank")) {
            sourceWalletHint = "ngân hàng";
        }

        // 5. Trích xuất gợi ý ví đích (dành riêng cho TRANSFER)
        String destinationHint = "";
        if (type == TransactionType.TRANSFER) {
            Pattern pDest = Pattern.compile("(?i)(?<![\\p{L}\\d])(?:sang|vào|đến)\\s+(?:ví\\s+)?([\\p{L}0-9_\\s]+)$");
            Matcher mDest = pDest.matcher(raw);
            if (mDest.find()) {
                destinationHint = mDest.group(1).trim();
            }
        }

        // 6. Trích xuất Ghi chú / Nội dung
        String note = raw;
        if (!amountMatchStr.isEmpty()) {
            note = note.replaceAll("(?i)" + Pattern.quote(amountMatchStr), "");
        }
        // Loại bỏ gợi ý thời gian tự nhiên khỏi ghi chú
        note = note.replaceAll("(?i)(?<![\\p{L}\\d])(hôm qua|hôm kia|hôm nay|sáng nay|tối qua|trưa nay|chiều nay|hom qua|hom kia|hom nay)(?![\\p{L}\\d])", " ");
        // Loại bỏ gợi ý phương thức thanh toán khỏi ghi chú
        note = note.replaceAll("(?i)(?<![\\p{L}\\d])(quẹt thẻ|thẻ tín dụng|thẻ visa|visa|mastercard|bằng thẻ|qua thẻ|ví momo|bắn momo|momo|tiền mặt|zalopay|zalo)(?![\\p{L}\\d])", " ");
        // Loại bỏ các từ cú pháp thừa
        note = note.replaceAll("(?i)(?<![\\p{L}\\d])(chi|thu|bắn|hết|mất|tiền|đã|đồng|dong|vnd|vnđ|d|đ|tổng cộng|hết thảy|vừa|mới|được)(?![\\p{L}\\d])", " ");
        note = note.trim().replaceAll("\\s+", " ");

        if (note.isEmpty()) {
            if (type == TransactionType.INCOME) note = "Thu nhập";
            else if (type == TransactionType.TRANSFER) note = "Chuyển tiền";
            else note = "Chi tiêu";
        } else {
            note = Character.toUpperCase(note.charAt(0)) + (note.length() > 1 ? note.substring(1) : "");
        }

        return new ParsedResult(type, amount, note, raw, destinationHint, sourceWalletHint, occurredAt);
    }

    private static String parseWordsAmount(String lower) {
        String[] patterns = {
            "(một|hai|ba|bốn|năm|sáu|bảy|tám|chín|mười)\\s*triệu\\s*rưỡi",
            "(một|hai|ba|bốn|năm|sáu|bảy|tám|chín|mười|hai mươi|năm mươi|một trăm)\\s*triệu",
            "một\\s*trăm\\s*(năm\\s*mươi|năm|rưỡi)\\s*(nghìn|ngàn)?",
            "hai\\s*trăm\\s*rưỡi\\s*(nghìn|ngàn)?",
            "năm\\s*trăm\\s*rưỡi\\s*(nghìn|ngàn)?",
            "(một|hai|ba|bốn|năm|sáu|bảy|tám|chín)\\s*trăm\\s*(nghìn|ngàn)",
            "(mười|hai mươi|ba mươi|bốn mươi|năm mươi|sáu mươi|bảy mươi|tám mươi|chín mươi)\\s*(lăm|năm|mốt|một|hai|ba|bốn|sáu|bảy|tám|chín)?\\s*(nghìn|ngàn)",
            "(hai|ba|bốn|năm|sáu|bảy|tám|chín)\\s*chục(\\s*(nghìn|ngàn))?",
            "(hai|ba|năm)\\s*lít"
        };

        for (String p : patterns) {
            Pattern pattern = Pattern.compile(p);
            Matcher matcher = pattern.matcher(lower);
            if (matcher.find()) {
                return matcher.group(0);
            }
        }
        return "";
    }

    private static long calculateWordsAmount(String words) {
        String clean = words.toLowerCase(Locale.ROOT).trim();

        if (clean.contains("triệu rưỡi")) {
            for (Map.Entry<String, Integer> e : WORD_DIGITS.entrySet()) {
                if (clean.startsWith(e.getKey())) {
                    return e.getValue() * 1_000_000L + 500_000L;
                }
            }
            return 1_500_000L;
        }

        if (clean.contains("triệu")) {
            if (clean.contains("một trăm")) return 100_000_000L;
            if (clean.contains("năm mươi")) return 50_000_000L;
            if (clean.contains("hai mươi")) return 20_000_000L;
            for (Map.Entry<String, Integer> e : WORD_DIGITS.entrySet()) {
                if (clean.startsWith(e.getKey())) {
                    return e.getValue() * 1_000_000L;
                }
            }
        }

        if (clean.contains("trăm rưỡi")) {
            if (clean.startsWith("hai")) return 250_000L;
            if (clean.startsWith("năm")) return 550_000L;
            return 150_000L;
        }

        if (clean.contains("trăm năm mươi") || clean.contains("trăm lăm")) {
            return 150_000L;
        }

        if (clean.contains("trăm")) {
            for (Map.Entry<String, Integer> e : WORD_DIGITS.entrySet()) {
                if (clean.startsWith(e.getKey())) {
                    return e.getValue() * 100_000L;
                }
            }
        }

        if (clean.contains("lít")) {
            for (Map.Entry<String, Integer> e : WORD_DIGITS.entrySet()) {
                if (clean.startsWith(e.getKey())) {
                    return e.getValue() * 100_000L;
                }
            }
        }

        if (clean.contains("chục")) {
            for (Map.Entry<String, Integer> e : WORD_DIGITS.entrySet()) {
                if (clean.startsWith(e.getKey())) {
                    return e.getValue() * 10_000L;
                }
            }
        }

        if (clean.contains("nghìn") || clean.contains("ngàn")) {
            long tens = 0;
            if (clean.contains("hai mươi") || clean.contains("hai muoi")) tens = 20;
            else if (clean.contains("ba mươi") || clean.contains("ba muoi")) tens = 30;
            else if (clean.contains("bốn mươi") || clean.contains("bon muoi")) tens = 40;
            else if (clean.contains("năm mươi") || clean.contains("nam muoi")) tens = 50;
            else if (clean.contains("sáu mươi") || clean.contains("sau muoi")) tens = 60;
            else if (clean.contains("bảy mươi") || clean.contains("bay muoi")) tens = 70;
            else if (clean.contains("tám mươi") || clean.contains("tam muoi")) tens = 80;
            else if (clean.contains("chín mươi") || clean.contains("chin muoi")) tens = 90;
            else if (clean.contains("mười") || clean.contains("muoi")) tens = 10;

            long units = 0;
            for (Map.Entry<String, Integer> e : WORD_DIGITS.entrySet()) {
                if (e.getValue() < 10 && clean.contains(" " + e.getKey())) {
                    units = e.getValue();
                    break;
                }
            }
            return (tens + units) * 1_000L;
        }

        return 0;
    }
}
