package com.example.fintrack.app.transaction.domain;

import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.FinanceFailure;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trình phân tích văn bản hóa đơn OCR tiếng Việt và quốc tế.
 * Trích xuất: Tổng tiền, Cửa hàng (Merchant/Payee), Thời gian (Date), Danh mục gợi ý (Category prediction), và Ghi chú (Note).
 * Nguyên tắc thận trọng: Tổng tiền không rõ ràng hoặc gây tranh chấp luôn để trống và yêu cầu người dùng xác nhận.
 */
public final class TransactionParser {

    public static final class Draft {
        public final long amount;
        public final String payee;
        public final Long date; // Epoch millis hoặc null nếu không nhận diện được
        public final String categoryHint; // ID danh mục: food, transport, shopping, bills, other-expense
        public final String note; // Nội dung ghi chú / tóm tắt các món
        public final int itemCount; // Số lượng mặt hàng ước tính
        public final boolean amountUncertain;
        public final boolean payeeUncertain;
        public final boolean dateUncertain;
        public final boolean categoryUncertain;

        // Constructor tương thích ngược cho các phiên bản và kiểm thử trước
        public Draft(long amount, String payee) {
            this(amount, payee, null, null, "", 0,
                    amount <= 0,
                    payee == null || payee.trim().isEmpty(),
                    true,
                    true);
        }

        public Draft(long amount, String payee, Long date, String categoryHint, String note, int itemCount,
                     boolean amountUncertain, boolean payeeUncertain, boolean dateUncertain, boolean categoryUncertain) {
            this.amount = amount;
            this.payee = payee != null ? payee : "";
            this.date = date;
            this.categoryHint = categoryHint;
            this.note = note != null ? note : "";
            this.itemCount = itemCount;
            this.amountUncertain = amountUncertain;
            this.payeeUncertain = payeeUncertain;
            this.dateUncertain = dateUncertain;
            this.categoryUncertain = categoryUncertain;
        }
    }

    public static Draft parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new Draft(0, "", null, null, "", 0, true, true, true, true);
        }

        String[] lines = raw.split("\\r?\\n");
        Pattern number = Pattern.compile("(?<![0-9.,])(?:[0-9]{1,3}(?:[.,][0-9]{3})+|[0-9]+)(?![0-9.,])");

        // 1. Trích xuất tên Cửa hàng / Đơn vị (Payee/Merchant)
        String payee = extractPayee(lines);

        // 2. Trích xuất Số tiền tổng cộng (Total Amount)
        Set<Long> candidates = new LinkedHashSet<>();
        int totalLineIndex = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String folded = fold(line);

            boolean isTotalLine = (folded.matches(".*\\btong cong\\b.*") && !folded.contains("tong cong ty"))
                    || folded.contains("tong tien")
                    || (folded.contains("thanh toan") && !folded.contains("hinh thuc thanh toan") && !folded.contains("phuong thuc thanh toan") && !folded.contains("tai khoan thanh toan"))
                    || folded.contains("tien thanh toan")
                    || folded.contains("tong thanh toan")
                    || folded.contains("phai thanh toan")
                    || folded.contains("khach phai tra")
                    || folded.contains("cong tien hang")
                    || folded.contains("amount due")
                    || folded.contains("net amount")
                    || folded.contains("grand total")
                    || folded.matches(".*\\btotal\\b.*")
                    || folded.matches(".*\\btong\\s*[:=].*");

            if (!isTotalLine) continue;

            // Bỏ qua các dòng tạm tính, tiền thừa, giảm giá, điểm tích lũy
            if (folded.contains("sub")
                    || folded.contains("tam tinh")
                    || folded.contains("khach dua")
                    || folded.contains("tien thua")
                    || folded.contains("tien tra lai")
                    || folded.contains("tra lai")
                    || folded.contains("change")
                    || folded.contains("chiet khau")
                    || folded.contains("giam gia")
                    || folded.contains("discount")
                    || folded.contains("diem tich luy")
                    || folded.contains("tong so luong")
                    || folded.contains("tong so mon")) {
                continue;
            }

            totalLineIndex = i;
            Matcher matcher = number.matcher(line);
            Long value = null;
            int numbers = 0;
            while (matcher.find()) {
                numbers++;
                try {
                    value = FinanceBook.parseAmount(matcher.group().replace(',', '.'));
                } catch (FinanceFailure ignored) {
                    value = null;
                }
            }

            if (value != null && numbers == 1) {
                candidates.add(value);
            } else if (numbers == 0 && i + 1 < lines.length && folded.matches("^(?:tong cong|total|thanh toan|tong tien|tong)\\s*[:=]?$")) {
                // Hóa đơn dạng 2 cột: Dòng hiện tại là "Tổng cộng", dòng kế tiếp là số tiền
                String nextLine = lines[i + 1].trim();
                Matcher nextMatcher = number.matcher(nextLine);
                Long nextValue = null;
                int nextNumbers = 0;
                while (nextMatcher.find()) {
                    nextNumbers++;
                    try {
                        nextValue = FinanceBook.parseAmount(nextMatcher.group().replace(',', '.'));
                    } catch (FinanceFailure ignored) {
                        nextValue = null;
                    }
                }
                if (nextValue != null && nextNumbers == 1) {
                    candidates.add(nextValue);
                }
            }
        }

        long amount = candidates.size() == 1 ? candidates.iterator().next() : 0;

        // 3. Trích xuất Thời gian (Date & Time)
        Long date = extractDate(lines);

        // 4. Dự đoán Danh mục (Category Prediction)
        String categoryHint = predictCategory(raw, payee);

        // 5. Trích xuất Ghi chú & Số lượng món (Items & Note)
        ExtractedItems items = extractItemsAndNote(lines, payee, totalLineIndex);

        boolean amountUncertain = (amount <= 0);
        boolean payeeUncertain = (payee == null || payee.trim().isEmpty());
        boolean dateUncertain = (date == null);
        boolean categoryUncertain = (categoryHint == null);

        return new Draft(amount, payee, date, categoryHint, items.note, items.count,
                amountUncertain, payeeUncertain, dateUncertain, categoryUncertain);
    }

    private static String extractPayee(String[] lines) {
        String legacyPayee = "";
        String cleanPayee = "";

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            if (legacyPayee.isEmpty()) {
                legacyPayee = line.substring(0, Math.min(160, line.length()));
            }

            String folded = fold(line);
            if (isBoilerplateHeader(folded)) continue;
            if (folded.startsWith("dt:") || folded.startsWith("tel:") || folded.startsWith("phone:")
                    || folded.startsWith("mst:") || folded.startsWith("ma so thue:")
                    || folded.startsWith("dia chi:") || folded.startsWith("d/c:")
                    || folded.startsWith("hotline:") || folded.startsWith("wifi:")
                    || folded.startsWith("pass:")) {
                continue;
            }

            // Loại bỏ tiền tố nếu có
            String candidate = line.replaceFirst("(?i)^(đơn vị|cửa hàng|chi nhánh|store|merchant|shop)\\s*[:\\-]\\s*", "").trim();
            if (candidate.length() >= 2 && candidate.length() <= 80 && cleanPayee.isEmpty()) {
                cleanPayee = candidate;
                break;
            }
        }

        return !cleanPayee.isEmpty() ? cleanPayee : legacyPayee;
    }

    private static boolean isBoilerplateHeader(String folded) {
        return folded.equals("cong hoa xa hoi chu nghia viet nam")
                || folded.equals("doc lap - tu do - hanh phuc")
                || folded.contains("hoa don ban le")
                || folded.contains("hoa don ban hang")
                || folded.contains("hoa don thanh toan")
                || folded.contains("hoa don gtgt")
                || folded.contains("hoa don gia tri gia tang")
                || folded.contains("hoa don vat")
                || folded.contains("phieu thanh toan")
                || folded.contains("phieu tinh tien")
                || folded.contains("phieu thu")
                || folded.contains("bien lai thu tien")
                || folded.equals("receipt")
                || folded.equals("tax invoice")
                || folded.equals("invoice")
                || folded.equals("bill")
                || folded.contains("chao mung quy khach")
                || folded.contains("cam on quy khach");
    }

    private static Long extractDate(String[] lines) {
        Pattern pDateDmy = Pattern.compile("(?<!\\d)(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2,4})(?!\\d)");
        Pattern pDateYmd = Pattern.compile("(?<!\\d)(\\d{4})[/-](\\d{1,2})[/-](\\d{1,2})(?!\\d)");
        Pattern pDateText = Pattern.compile("(?i)ngay\\s*(\\d{1,2})\\s*thang\\s*(\\d{1,2})\\s*nam\\s*(\\d{4})");
        Pattern pTime = Pattern.compile("(?<!\\d)(\\d{1,2})[:h](\\d{2})(?:[:](\\d{2}))?(?!\\d)");

        for (String line : lines) {
            String trimmed = line.trim();
            int day = -1, month = -1, year = -1;

            Matcher mText = pDateText.matcher(trimmed);
            if (mText.find()) {
                try {
                    day = Integer.parseInt(mText.group(1));
                    month = Integer.parseInt(mText.group(2));
                    year = Integer.parseInt(mText.group(3));
                } catch (Exception ignored) {}
            }

            if (year < 0) {
                Matcher mDmy = pDateDmy.matcher(trimmed);
                if (mDmy.find()) {
                    try {
                        day = Integer.parseInt(mDmy.group(1));
                        month = Integer.parseInt(mDmy.group(2));
                        year = Integer.parseInt(mDmy.group(3));
                        if (year < 100) year += 2000;
                    } catch (Exception ignored) {}
                }
            }

            if (year < 0) {
                Matcher mYmd = pDateYmd.matcher(trimmed);
                if (mYmd.find()) {
                    try {
                        year = Integer.parseInt(mYmd.group(1));
                        month = Integer.parseInt(mYmd.group(2));
                        day = Integer.parseInt(mYmd.group(3));
                    } catch (Exception ignored) {}
                }
            }

            if (year >= 2000 && year <= 2099 && month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                int hour = 12, minute = 0, second = 0;
                Matcher mTime = pTime.matcher(trimmed);
                if (mTime.find()) {
                    try {
                        hour = Integer.parseInt(mTime.group(1));
                        minute = Integer.parseInt(mTime.group(2));
                        if (mTime.group(3) != null) {
                            second = Integer.parseInt(mTime.group(3));
                        }
                    } catch (Exception ignored) {}
                }

                if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59 && second >= 0 && second <= 59) {
                    Calendar cal = Calendar.getInstance(FinanceBook.ZONE);
                    cal.set(Calendar.YEAR, year);
                    cal.set(Calendar.MONTH, month - 1);
                    cal.set(Calendar.DAY_OF_MONTH, day);
                    cal.set(Calendar.HOUR_OF_DAY, hour);
                    cal.set(Calendar.MINUTE, minute);
                    cal.set(Calendar.SECOND, second);
                    cal.set(Calendar.MILLISECOND, 0);
                    return cal.getTimeInMillis();
                }
            }
        }
        return null;
    }

    private static String predictCategory(String raw, String payee) {
        String foldedRaw = fold(raw);
        String foldedPayee = fold(payee);

        String[] foodKeywords = new String[]{
                "coffee", "cafe", "ca phe", "highlands", "phuc long", "starbucks", "trung nguyen", "the coffee house", "passio",
                "tra sua", "gong cha", "ding tea", "tocotoco", "koi the", "mixue",
                "quan an", "quan dong", "quan", "nha hang", "restaurant", "dining", "com", "pho", "bun", "mi", "hu tieu", "banh mi",
                "lau", "nuong", "bbq", "pizza", "kfc", "lotteria", "jollibee", "mcdonald", "burger",
                "bakery", "banh ngot", "banh kem", "kem", "do uong", "nuoc ngot", "bia", "bar", "pub", "food", "latte", "cappuccino", "espresso"
        };

        String[] transportKeywords = new String[]{
                "grab", "be group", "gojek", "xanh sm", "taxi", "mai linh", "vinasun",
                "petrolimex", "pvoil", "xang", "dau diesel", "ve xe", "xe bus", "xe buyt", "ben xe", "san bay", "gui xe", "giu xe",
                "ve tau", "vietnam airlines", "vietjet", "bamboo", "phi cau duong", "toll", "parking", "sua xe", "rua xe"
        };

        String[] shoppingKeywords = new String[]{
                "sieu thi", "supermarket", "mart", "winmart", "vinmart", "co.op", "coopmart", "bach hoa xanh",
                "big c", "go!", "lotte mart", "aeon", "circle k", "gs25", "7-eleven", "familymart", "minimart",
                "shopee", "lazada", "tiki", "sendo", "tiktok shop",
                "thoi trang", "quan ao", "giay dep", "uniqlo", "zara", "h&m", "routine",
                "my pham", "hasaki", "guardian", "watsons",
                "dien may", "dien may xanh", "fpt shop", "the gioi di dong", "cellphones", "shop"
        };

        String[] billsKeywords = new String[]{
                "dien luc", "evn", "tien dien", "tien nuoc", "cap nuoc", "sawaco",
                "internet", "viettel", "vnpt", "fpt telecom", "truyen hinh",
                "hoa don tien", "phi dich vu", "phi quan ly", "cuoc vien thong", "utility", "dien thoai"
        };

        String[] otherKeywords = new String[]{
                "nha thuoc", "pharmacity", "long chau", "an khang", "benh vien", "phong kham", "bac si", "thuoc", "cgv", "bhd", "cinema"
        };

        int foodScore = countMatches(foldedRaw, foodKeywords) + countMatches(foldedPayee, foodKeywords) * 4;
        int transportScore = countMatches(foldedRaw, transportKeywords) + countMatches(foldedPayee, transportKeywords) * 4;
        int shoppingScore = countMatches(foldedRaw, shoppingKeywords) + countMatches(foldedPayee, shoppingKeywords) * 4;
        int billsScore = countMatches(foldedRaw, billsKeywords) + countMatches(foldedPayee, billsKeywords) * 4;
        int otherScore = countMatches(foldedRaw, otherKeywords) + countMatches(foldedPayee, otherKeywords) * 4;

        int maxScore = 0;
        String bestCategory = null;

        if (foodScore > maxScore) { maxScore = foodScore; bestCategory = "food"; }
        if (transportScore > maxScore) { maxScore = transportScore; bestCategory = "transport"; }
        if (shoppingScore > maxScore) { maxScore = shoppingScore; bestCategory = "shopping"; }
        if (billsScore > maxScore) { maxScore = billsScore; bestCategory = "bills"; }
        if (otherScore > maxScore) { bestCategory = "other-expense"; }

        return bestCategory;
    }

    private static int countMatches(String text, String[] keywords) {
        int count = 0;
        for (String kw : keywords) {
            if (text.contains(kw)) {
                count += 1;
            }
        }
        return count;
    }

    private static class ExtractedItems {
        final String note;
        final int count;
        ExtractedItems(String note, int count) {
            this.note = note;
            this.count = count;
        }
    }

    private static ExtractedItems extractItemsAndNote(String[] lines, String payee, int totalLineIndex) {
        List<String> items = new ArrayList<>();
        int count = 0;
        int limit = totalLineIndex > 0 ? Math.min(totalLineIndex, lines.length) : lines.length;

        for (int i = 0; i < limit; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.length() < 3 || line.length() > 50) continue;
            if (line.equalsIgnoreCase(payee)) continue;

            String folded = fold(line);
            if (isBoilerplateHeader(folded)) continue;
            if (folded.startsWith("dt:") || folded.startsWith("mst:") || folded.startsWith("ngay") || folded.startsWith("date")) continue;

            // Kiểm tra dòng có vẻ là mặt hàng (chứa chữ và có thể có số lượng/đơn giá)
            if (line.matches(".*[a-zA-ZÀ-ỹ]{3,}.*")) {
                count++;
                if (items.size() < 2) {
                    // Cắt bớt phần số/giá ở cuối dòng nếu có
                    String itemName = line.replaceFirst("\\s+\\d+[.,]?\\d*.*$", "").trim();
                    if (!itemName.isEmpty() && !items.contains(itemName)) {
                        items.add(itemName);
                    }
                }
            }
        }

        String note = "";
        if (!items.isEmpty()) {
            note = String.join(", ", items);
        } else if (payee != null && !payee.isEmpty()) {
            note = payee;
        }

        return new ExtractedItems(note, count);
    }

    private static String fold(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    private TransactionParser() { }
}
