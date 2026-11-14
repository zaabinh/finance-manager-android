package com.example.fintrack.app.finance.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.text.Normalizer;
import com.example.fintrack.app.finance.domain.FinanceBook.Query;
import com.example.fintrack.app.finance.domain.FinanceBook.Page;

/** Extracted domain rules; no Android or persistence dependencies. */
final class TransactionQueryEngine {
    private TransactionQueryEngine() { }
    static List<Transaction> transactions(FinanceBook book, FinanceSnapshot.Builder state, String month, TransactionType type, String query, String walletId, String categoryId) {
        book.validateMonth(month);
        String search = fold(query == null ? "" : query.trim());
        List<Transaction> result = new ArrayList<>();
        for (Transaction item : state.transactions) {
            if ("VOIDED".equals(item.status) || !book.monthAt(item.occurredAt).equals(month) || (type != null && item.type != type)) continue;
            if (walletId != null && !walletId.equals(item.walletId) && !walletId.equals(item.destinationWalletId)) continue;
            if (categoryId != null && !categoryId.equals(item.categoryId)) continue;
            Category category = book.category(item.categoryId);
            Wallet wallet = book.wallet(item.walletId), destination = book.wallet(item.destinationWalletId);

            // Tạo chuỗi gộp để tìm kiếm từ khóa linh hoạt (ghi chú, người nhận, tên danh mục, ví, số tiền)
            String haystack = item.note + " " + item.payee + " " + (category == null ? "" : category.name)
                    + " " + (wallet == null ? "" : wallet.name) + " " + (destination == null ? "" : destination.name)
                    + " " + item.amount;
            if (fold(haystack).contains(search)) result.add(item);
        }
        // Sắp xếp danh sách giao dịch giảm dần theo thời gian thực hiện mới nhất
        result.sort(Comparator.comparingLong((Transaction item) -> item.occurredAt).reversed().thenComparing(item -> item.id));
        return Collections.unmodifiableList(result);
    }
    static Page query(FinanceBook book, FinanceSnapshot.Builder state, Query query, int page, int size) {
        java.text.SimpleDateFormat days = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        days.setTimeZone(TimeZone.getTimeZone(state.timeZone));
        String search = query.q == null ? null : query.q.toLowerCase(Locale.ROOT);
        List<Transaction> matches = new ArrayList<>();
        for (Transaction item : state.transactions) {
            if ("VOIDED".equals(item.status) || (query.type != null && item.type != query.type)) continue;
            String day = days.format(new java.util.Date(item.occurredAt));
            if ((query.dateFrom != null && day.compareTo(query.dateFrom) < 0) || (query.dateTo != null && day.compareTo(query.dateTo) > 0)) continue;
            // A transfer matches its source or its destination wallet (contract section 5).
            if (query.walletId != null && !query.walletId.equals(item.walletId) && !query.walletId.equals(item.destinationWalletId)) continue;
            if (query.categoryId != null && !query.categoryId.equals(item.categoryId)) continue;
            if (search != null && !item.note.toLowerCase(Locale.ROOT).contains(search)
                    && !item.payee.toLowerCase(Locale.ROOT).contains(search)) continue;
            matches.add(item);
        }
        Comparator<Transaction> order = query.sort.startsWith("amountMinor")
                ? Comparator.comparingLong((Transaction item) -> item.amount) : Comparator.comparingLong((Transaction item) -> item.occurredAt);
        order = order.thenComparing(item -> item.id);
        if (query.sort.endsWith(",desc")) order = order.reversed();
        matches.sort(order);
        int from = (int) Math.min((long) page * size, matches.size());
        return new Page(Collections.unmodifiableList(new ArrayList<>(matches.subList(from, Math.min(from + size, matches.size())))),
                page, (matches.size() + size - 1) / size);
    }
    static List<Transaction> merge(List<Transaction> rows, List<Transaction> next) {
        List<Transaction> result = new ArrayList<>(rows);
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Transaction item : rows) ids.add(item.id);
        for (Transaction item : next) if (ids.add(item.id)) result.add(item);
        return Collections.unmodifiableList(result);
    }
    static String fold(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
