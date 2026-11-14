package com.example.fintrack.app.voice.domain;

import com.example.fintrack.app.finance.domain.TransactionType;

import java.util.HashMap;
import java.util.List;
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

        // Trường hợp 2b: Chai (1 chai = 1 triệu, 2 chai rưỡi = 2.5 triệu, 2.5 chai)
        if (amount == 0) {
            Pattern pChai = Pattern.compile("(\\d+([.,]\\d+)?)\\s*(chai)(\\s*(rưỡi|ruoi))?(?![\\p{L}\\d])");
            Matcher mChai = pChai.matcher(lower);
            if (mChai.find()) {
                try {
                    String numStr = mChai.group(1).replace(",", ".");
                    double val = Double.parseDouble(numStr);
                    amount = (long) (val * 1_000_000L);
                    if (mChai.group(4) != null) {
                        amount += 500_000L;
                    }
                    amountMatchStr = mChai.group(0);
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

        // Trường hợp 4: Trăm đi kèm số chục (2 trăm 3 = 230k, 1 trăm 2 = 120k, 5 trăm 8 = 580k)
        if (amount == 0) {
            Pattern pTramChuc = Pattern.compile("(\\d+)\\s*(trăm|tram)\\s*(mốt|mot|hai|ba|bốn|bon|tư|tu|rưỡi|ruoi|sáu|sau|bảy|bay|tám|tam|chín|chin|\\d)(?![\\p{L}\\d])");
            Matcher mTramChuc = pTramChuc.matcher(lower);
            if (mTramChuc.find()) {
                try {
                    long hundredVal = Long.parseLong(mTramChuc.group(1));
                    String second = mTramChuc.group(3);
                    long offset = 0;
                    if (second.matches("\\d")) {
                        offset = Long.parseLong(second) * 10_000L;
                    } else if (second.equals("mốt") || second.equals("mot")) {
                        offset = 10_000L;
                    } else if (second.equals("hai")) {
                        offset = 20_000L;
                    } else if (second.equals("ba")) {
                        offset = 30_000L;
                    } else if (second.equals("bốn") || second.equals("bon") || second.equals("tư") || second.equals("tu")) {
                        offset = 40_000L;
                    } else if (second.equals("rưỡi") || second.equals("ruoi")) {
                        offset = 50_000L;
                    } else if (second.equals("sáu") || second.equals("sau")) {
                        offset = 60_000L;
                    } else if (second.equals("bảy") || second.equals("bay")) {
                        offset = 70_000L;
                    } else if (second.equals("tám") || second.equals("tam")) {
                        offset = 80_000L;
                    } else if (second.equals("chín") || second.equals("chin")) {
                        offset = 90_000L;
                    }
                    amount = hundredVal * 100_000L + offset;
                    amountMatchStr = mTramChuc.group(0);
                } catch (Exception ignored) {}
            }
        }

        // Trường hợp 4b: Trăm (1 trăm, 2 trăm rưỡi = 250k, 5 trăm = 500k)
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

        // Trường hợp 5b: Lốp / Loét / Vé (1 lốp = 100k, 2 vé = 200k)
        if (amount == 0) {
            Pattern pLop = Pattern.compile("(\\d+)\\s*(lốp|lop|loét|loet|vé|ve)(\\s*(rưỡi|ruoi))?(?![\\p{L}\\d])");
            Matcher mLop = pLop.matcher(lower);
            if (mLop.find()) {
                try {
                    long val = Long.parseLong(mLop.group(1));
                    amount = val * 100_000L + (mLop.group(3) != null ? 50_000L : 0);
                    amountMatchStr = mLop.group(0);
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

        // Trường hợp 6b: Sọi (5 sọi = 50k, 2 sọi = 20k)
        if (amount == 0) {
            Pattern pSoi = Pattern.compile("(\\d+)\\s*(sọi|soi)(?![\\p{L}\\d])");
            Matcher mSoi = pSoi.matcher(lower);
            if (mSoi.find()) {
                try {
                    long val = Long.parseLong(mSoi.group(1));
                    amount = val * 10_000L;
                    amountMatchStr = mSoi.group(0);
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
            } else if (lower.contains("nửa chai") || lower.contains("nua chai")) {
                amount = 500_000L;
                amountMatchStr = lower.contains("nửa chai") ? "nửa chai" : "nua chai";
            } else if (lower.contains("nửa củ") || lower.contains("nua cu")) {
                amount = 500_000L;
                amountMatchStr = lower.contains("nửa củ") ? "nửa củ" : "nua cu";
            } else if (lower.contains("nửa lít") || lower.contains("nua lit") || lower.contains("nửa lốp") || lower.contains("nua lop") || lower.contains("nửa vé") || lower.contains("nua ve")) {
                amount = 50_000L;
                amountMatchStr = lower.contains("nửa lít") ? "nửa lít" : (lower.contains("nửa lốp") ? "nửa lốp" : "nửa vé");
            } else if (lower.contains("trăm hai") || lower.contains("tram hai")) {
                amount = 120_000L;
                amountMatchStr = lower.contains("trăm hai") ? "trăm hai" : "tram hai";
            } else if (lower.contains("trăm ba") || lower.contains("tram ba")) {
                amount = 130_000L;
                amountMatchStr = lower.contains("trăm ba") ? "trăm ba" : "tram ba";
            } else if (lower.contains("trăm tư") || lower.contains("tram tu") || lower.contains("trăm bốn") || lower.contains("tram bon")) {
                amount = 140_000L;
                amountMatchStr = lower.contains("trăm tư") ? "trăm tư" : "tram tu";
            } else if (lower.contains("trăm sáu") || lower.contains("tram sau")) {
                amount = 160_000L;
                amountMatchStr = lower.contains("trăm sáu") ? "trăm sáu" : "tram sau";
            } else if (lower.contains("trăm bảy") || lower.contains("tram bay")) {
                amount = 170_000L;
                amountMatchStr = lower.contains("trăm bảy") ? "trăm bảy" : "tram bay";
            } else if (lower.contains("trăm tám") || lower.contains("tram tam")) {
                amount = 180_000L;
                amountMatchStr = lower.contains("trăm tám") ? "trăm tám" : "tram tam";
            } else if (lower.contains("trăm chín") || lower.contains("tram chin")) {
                amount = 190_000L;
                amountMatchStr = lower.contains("trăm chín") ? "trăm chín" : "tram chin";
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
        note = note.replaceAll("(?i)(?<![\\p{L}\\d])(chi|tiêu|thu|bắn|hết|mất|tiền|đã|đồng|dong|vnd|vnđ|d|đ|tổng cộng|hết thảy|vừa|mới|được)(?![\\p{L}\\d])", " ");
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
            "(một|hai|ba|bốn|năm|sáu|bảy|tám|chín)\\s*trăm\\s*(mốt|hai|ba|bốn|tư|rưỡi|sáu|bảy|tám|chín)(?![\\p{L}\\d])",
            "(hăm|hai|ba|bốn|năm|sáu|bảy|tám|chín)\\s*(lăm|nhăm)(\\s*(nghìn|ngàn|k))?",
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

        if (clean.contains("lăm") || clean.contains("nhăm")) {
            long tens = 0;
            if (clean.contains("nhăm") || clean.startsWith("năm")) tens = 50;
            else if (clean.contains("hăm") || clean.contains("hai")) tens = 20;
            else if (clean.contains("ba")) tens = 30;
            else if (clean.contains("bốn")) tens = 40;
            else if (clean.contains("sáu")) tens = 60;
            else if (clean.contains("bảy")) tens = 70;
            else if (clean.contains("tám")) tens = 80;
            else if (clean.contains("chín")) tens = 90;
            else if (clean.contains("mười")) tens = 10;
            if (tens > 0) {
                return (tens + 5) * 1_000L;
            }
        }

        Pattern pWordHundredTens = Pattern.compile("(một|hai|ba|bốn|năm|sáu|bảy|tám|chín)\\s*trăm\\s*(mốt|hai|ba|bốn|tư|rưỡi|sáu|bảy|tám|chín)");
        Matcher mWordHundredTens = pWordHundredTens.matcher(clean);
        if (mWordHundredTens.find()) {
            String w1 = mWordHundredTens.group(1);
            String w2 = mWordHundredTens.group(2);
            long h = WORD_DIGITS.containsKey(w1) ? WORD_DIGITS.get(w1) : 1;
            long tens = 0;
            if (w2.equals("mốt")) tens = 10_000L;
            else if (w2.equals("hai")) tens = 20_000L;
            else if (w2.equals("ba")) tens = 30_000L;
            else if (w2.equals("bốn") || w2.equals("tư")) tens = 40_000L;
            else if (w2.equals("rưỡi")) tens = 50_000L;
            else if (w2.equals("sáu")) tens = 60_000L;
            else if (w2.equals("bảy")) tens = 70_000L;
            else if (w2.equals("tám")) tens = 80_000L;
            else if (w2.equals("chín")) tens = 90_000L;
            return h * 100_000L + tens;
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

    /**
     * Duyệt mảng các phỏng đoán N-Best từ SpeechRecognizer để chọn câu tốt nhất có chứa số tiền.
     */
    public static ParsedResult parseBest(List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return parse("");
        }
        ParsedResult firstNonEmpty = null;
        for (String cand : candidates) {
            if (cand == null || cand.trim().isEmpty()) continue;
            ParsedResult res = parse(cand);
            if (res.amount > 0) {
                return res; // Tìm thấy câu phỏng đoán có số tiền hợp lệ -> chọn ngay!
            }
            if (firstNonEmpty == null) {
                firstNonEmpty = res;
            }
        }
        return firstNonEmpty != null ? firstNonEmpty : parse(candidates.get(0));
    }

    /**
     * Kiểm tra xem ghi chú có phải là từ mặc định/chung chung (chưa có nội dung cụ thể) hay không.
     */
    public static boolean isGenericNote(String note) {
        if (note == null || note.trim().isEmpty()) return true;
        String clean = note.trim().toLowerCase(Locale.ROOT);
        return clean.equals("chi tiêu") || clean.equals("chi tieu")
                || clean.equals("thu nhập") || clean.equals("thu nhap")
                || clean.equals("chuyển tiền") || clean.equals("chuyen tien")
                || clean.equals("khoản chi") || clean.equals("khoan chi")
                || clean.equals("khoản thu") || clean.equals("khoan thu")
                || clean.equals("tiền") || clean.equals("tien")
                || clean.equals("giao dịch") || clean.equals("giao dich");
    }

    /**
     * Bóc tách gợi ý tên ví từ câu nói bổ sung (ví dụ: "sang momo", "vào ví tiền mặt", "momo").
     */
    public static String extractWalletHintFromSupplement(String text) {
        if (text == null || text.trim().isEmpty()) return "";
        String clean = text.trim();
        Pattern p = Pattern.compile("(?i)(?:sang|vào|đến|qua)\\s+(?:ví\\s+)?([\\p{L}0-9_\\s]+)");
        Matcher m = p.matcher(clean);
        if (m.find()) {
            return m.group(1).trim();
        }
        return clean.replaceAll("(?i)^(?:ví|vi)\\s+", "").trim();
    }

    /**
     * Ghép nối ngữ cảnh hội thoại đa lượt (Multi-turn Conversational Merge):
     * Bổ sung thông tin còn thiếu (số tiền, nội dung, ví nhận) từ câu nói lượt sau vào kết quả lượt trước.
     */
    public static ParsedResult mergeSupplement(ParsedResult base, List<String> supplementCandidates) {
        if (base == null) {
            return parseBest(supplementCandidates);
        }
        if (supplementCandidates == null || supplementCandidates.isEmpty()) {
            return base;
        }
        ParsedResult supp = parseBest(supplementCandidates);

        TransactionType type = base.type;
        long amount = base.amount;
        String note = base.note;
        String destWallet = base.destinationWalletHint;
        String srcWallet = base.sourceWalletHint;
        long occurredAt = base.occurredAt;

        // 1. Bổ sung số tiền nếu base đang thiếu số tiền (amount <= 0)
        if (amount <= 0 && supp.amount > 0) {
            amount = supp.amount;
            if (isGenericNote(note) && !isGenericNote(supp.note)) {
                note = supp.note;
            }
        } else if (supp.amount > 0 && isGenericNote(note)) {
            // Base chỉ có số tiền chung chung, câu sau nói rõ hơn
            amount = supp.amount;
            if (!isGenericNote(supp.note)) {
                note = supp.note;
            }
        }

        // 2. Bổ sung nội dung nếu base chỉ có nội dung chung chung (generic note)
        if (isGenericNote(note)) {
            if (!isGenericNote(supp.note)) {
                note = supp.note;
            } else {
                for (String c : supplementCandidates) {
                    ParsedResult pr = parse(c);
                    if (!isGenericNote(pr.note)) {
                        note = pr.note;
                        break;
                    }
                }
            }
        }

        // 3. Bổ sung ví nhận (khi chuyển khoản)
        if (type == TransactionType.TRANSFER && destWallet.isEmpty()) {
            if (!supp.destinationWalletHint.isEmpty()) {
                destWallet = supp.destinationWalletHint;
            } else {
                for (String c : supplementCandidates) {
                    String hint = extractWalletHintFromSupplement(c);
                    if (!hint.isEmpty()) {
                        destWallet = hint;
                        break;
                    }
                }
            }
        }

        // 4. Bổ sung ví nguồn nếu base chưa có mà câu sau nhắc đến
        if (srcWallet.isEmpty() && !supp.sourceWalletHint.isEmpty()) {
            srcWallet = supp.sourceWalletHint;
        }

        String mergedTranscript = base.rawTranscript + " + " + supp.rawTranscript;
        return new ParsedResult(type, amount, note, mergedTranscript, destWallet, srcWallet, occurredAt);
    }
}
