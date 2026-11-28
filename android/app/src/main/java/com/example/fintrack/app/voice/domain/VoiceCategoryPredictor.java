package com.example.fintrack.app.voice.domain;

import com.example.fintrack.app.finance.domain.Category;
import com.example.fintrack.app.finance.domain.TransactionType;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Dự đoán danh mục thu chi thông minh từ giọng nói và ghi chú giao dịch tiếng Việt.
 */
public final class VoiceCategoryPredictor {

    private VoiceCategoryPredictor() {}

    /**
     * Tìm danh mục phù hợp nhất từ danh sách danh mục hiện có của người dùng.
     */
    public static Category predictCategory(List<Category> categories, TransactionType type, String note, String rawTranscript) {
        if (categories == null || categories.isEmpty() || type == TransactionType.TRANSFER) {
            return null;
        }

        List<Category> active = new ArrayList<>();
        for (Category c : categories) {
            if (c.type == type && c.archivedAt == null) {
                active.add(c);
            }
        }
        if (active.isEmpty()) return null;

        String combined = ((note != null ? note : "") + " " + (rawTranscript != null ? rawTranscript : "")).toLowerCase(Locale.ROOT);
        String normalizedCombined = stripAccents(combined);

        // 1. So khớp trực tiếp với tên danh mục (ví dụ "cà phê" khớp danh mục "Cà phê")
        for (Category c : active) {
            if (c.name != null && !c.name.trim().isEmpty()) {
                String cNameNorm = stripAccents(c.name.toLowerCase(Locale.ROOT));
                if (matchesWordOrPhrase(normalizedCombined, cNameNorm)) {
                    return c;
                }
            }
        }

        // 2. So khớp từ khóa ngữ nghĩa tiếng Việt
        if (type == TransactionType.EXPENSE) {
            // Đi lại / Di chuyển (Transport) - ưu tiên kiểm tra trước các từ chung
            if (matchesWordOrPhrase(normalizedCombined, "xang", "xe", "grab", "be", "gojek", "taxi", "gui xe", "ve xe", "ve tau",
                    "ve may bay", "sua xe", "rua xe", "bao duong", "cau duong", "giao thong")) {
                Category match = findByCategoryKeyword(active, "transport", "di lai", "di chuyen", "car");
                if (match != null) return match;
            }

            // Hóa đơn & Tiện ích (Bills & Utilities)
            if (matchesWordOrPhrase(normalizedCombined, "dien", "nuoc", "mang", "internet", "wifi", "tien nha", "thue nha", "hoc phi",
                    "phi", "truyen hinh", "chung cu", "hoa don", "gas", "rac")) {
                Category match = findByCategoryKeyword(active, "bills", "hoa don", "receipt");
                if (match != null) return match;
            }

            // Ăn uống (Food & Drink)
            if (matchesWordOrPhrase(normalizedCombined, "an", "uong", "com", "pho", "bun", "mi", "banh", "ca phe", "cafe", "coffee",
                    "tra sua", "nuoc", "thit", "ca", "cho", "trua", "toi", "sang", "nhau", "lau", "nuong", "quan an", "tra",
                    "snack", "hoa qua", "trai cay", "pizza", "kfc", "banh mi")) {
                Category match = findByCategoryKeyword(active, "food", "an", "coffee");
                if (match != null) return match;
            }

            // Mua sắm (Shopping)
            if (matchesWordOrPhrase(normalizedCombined, "mua", "quan ao", "ao", "quan", "giay", "dep", "sach", "shopee", "lazada",
                    "tiki", "sieu thi", "do", "my pham", "tui", "dong ho", "dien thoai", "laptop", "may tinh")) {
                Category match = findByCategoryKeyword(active, "shopping", "mua sam", "shopping_bag");
                if (match != null) return match;
            }

            // Sức khỏe / Y tế (Health)
            if (matchesWordOrPhrase(normalizedCombined, "thuoc", "kham", "benh vien", "bac si", "nha khoa", "rang", "gym", "the thao", "yoga", "bao hiem")) {
                Category match = findByCategoryKeyword(active, "health", "suc khoe", "y te", "hospital");
                if (match != null) return match;
            }

            // Giải trí (Entertainment)
            if (matchesWordOrPhrase(normalizedCombined, "phim", "cinema", "game", "du lich", "karaoke", "choi", "ve xem phim", "net", "bar", "pub")) {
                Category match = findByCategoryKeyword(active, "entertainment", "giai tri", "game");
                if (match != null) return match;
            }
        } else if (type == TransactionType.INCOME) {
            // Lương (Salary)
            if (matchesWordOrPhrase(normalizedCombined, "luong", "salary", "cong", "tien luong")) {
                Category match = findByCategoryKeyword(active, "salary", "luong", "briefcase");
                if (match != null) return match;
            }

            // Thưởng & Quà tặng (Bonus & Gifts)
            if (matchesWordOrPhrase(normalizedCombined, "thuong", "bonus", "li xi", "lixi", "hoa hong", "tien thuong")) {
                Category match = findByCategoryKeyword(active, "bonus", "thuong", "gift");
                if (match != null) return match;
            }
        }

        // Mặc định chọn danh mục đầu tiên đang hoạt động
        return active.get(0);
    }

    private static boolean matchesWordOrPhrase(String text, String... keywords) {
        for (String kw : keywords) {
            if (kw.contains(" ")) {
                if (text.contains(kw)) return true;
            } else {
                Pattern p = Pattern.compile("(?i)(?<![a-z0-9])" + Pattern.quote(kw) + "(?![a-z0-9])");
                if (p.matcher(text).find()) return true;
            }
        }
        return false;
    }

    private static Category findByCategoryKeyword(List<Category> categories, String... keys) {
        for (Category c : categories) {
            String idNorm = c.id != null ? c.id.toLowerCase(Locale.ROOT) : "";
            String nameNorm = stripAccents(c.name != null ? c.name.toLowerCase(Locale.ROOT) : "");
            String icon = c.iconKey != null ? c.iconKey.toLowerCase(Locale.ROOT) : "";
            for (String key : keys) {
                if (idNorm.equals(key) || nameNorm.contains(key) || icon.contains(key)) {
                    return c;
                }
            }
        }
        return null;
    }

    private static String stripAccents(String s) {
        if (s == null) return "";
        String normalized = Normalizer.normalize(s, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D');
    }
}
