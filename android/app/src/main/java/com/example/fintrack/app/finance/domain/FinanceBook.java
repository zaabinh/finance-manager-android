package com.example.fintrack.app.finance.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Lớp nghiệp vụ cốt lõi (Domain Model) quản lý Sổ tài chính (Ledger) tính bằng tiền VNĐ.
 * Số dư và hạn mức sử dụng ngân sách được tính toán trực tiếp, không lưu trữ riêng lẻ.
 */
public final class FinanceBook {

    // Hằng số giới hạn số tiền tối đa và múi giờ mặc định (Việt Nam)
    public static final long MAX_AMOUNT = 999_999_999_999L;
    public static final TimeZone ZONE = TimeZone.getTimeZone("Asia/Ho_Chi_Minh");

    // Các loại giao dịch: Chi tiêu, Thu nhập, Chuyển tiền giữa các ví
    public enum Type { EXPENSE, INCOME, TRANSFER }

    // Các loại ví tài khoản
    public enum WalletType { CASH, BANK, SAVINGS }

    // Danh sách các mã lỗi nghiệp vụ
    public enum Problem { AMOUNT, CATEGORY, WALLET, SAME_WALLET, DUPLICATE_BUDGET, NOT_FOUND, NAME, DATE, STORAGE,
        NETWORK, UNAUTHORIZED, CONFLICT, VALIDATION, SERVER, INSUFFICIENT_FUNDS, VOIDED, ARCHIVED }

    // Ngoại lệ dùng chung trong toàn bộ nghiệp vụ tài chính
    public static final class Failure extends RuntimeException {
        public final Problem problem;
        public final String userMessage;
        /** Backend errorCode and fieldErrors[].field when the server supplied them; for client-side localisation. */
        public String code;
        public List<String> fields = Collections.emptyList();
        public Failure(Problem problem) { this(problem, null); }
        public Failure(Problem problem, String message) { super(problem.name()); this.problem = problem; this.userMessage = message; }
    }

    // Mô hình dữ liệu Ví tiền
    public static final class Wallet {
        public String id, name;
        public long openingBalance;
        public String archivedAt;
        public boolean includeInAssets = true;
        public int sortOrder;
        public Long serverBalanceMinor;
        public WalletType walletType = WalletType.CASH;
        public Wallet(String id, String name, long openingBalance) {
            this.id = id; this.name = name; this.openingBalance = openingBalance;
        }
    }

    // Mô hình dữ liệu Danh mục thu/chi
    public static final class Category {
        public String id, name;
        public Type type;
        public String archivedAt, iconKey, colorHex;
        public int sortOrder;
        public Category(String id, String name, Type type) { this.id = id; this.name = name; this.type = type; }
    }

    // Mô hình dữ liệu Giao dịch tài chính
    public static final class Transaction {
        public String id, categoryId, walletId, destinationWalletId, note, payee;
        public Type type;
        public long amount, occurredAt;
        public String status = "POSTED", voidedAt;
        /** Idempotency key for POST; distinct from the (server-assigned) transaction id. */
        public String clientRequestId;
        public Transaction(String id, Type type, long amount, String categoryId, String walletId,
                           String destinationWalletId, long occurredAt, String note, String payee) {
            this.id = id; this.type = type; this.amount = amount; this.categoryId = categoryId;
            this.walletId = walletId; this.destinationWalletId = destinationWalletId;
            this.occurredAt = occurredAt; this.note = note; this.payee = payee;
        }
    }

    // Mô hình dữ liệu Hạn mức Ngân sách
    public static final class Budget {
        public String id, categoryId, month;
        public long limit;
        public Long serverSpentMinor;
        public int warningThresholdPercent = 80;
        public String archivedAt;
        public Budget(String id, String categoryId, String month, long limit) {
            this.id = id; this.categoryId = categoryId; this.month = month; this.limit = limit;
        }
    }

    /** Trạng thái lưu trữ dữ liệu (State), có phiên bản để tránh ghi đè dữ liệu bị lỗi/hỏng. */
    public static final class State {
        public int version = 1;
        public String timeZone = "Asia/Ho_Chi_Minh";
        public boolean serverSnapshot;
        public List<Wallet> wallets = new ArrayList<>();
        public List<Category> categories = new ArrayList<>();
        public List<Transaction> transactions = new ArrayList<>();
        public List<Budget> budgets = new ArrayList<>();
    }

    // Khởi tạo sổ tài chính mặc định với các danh mục cơ bản sẵn có
    public static State empty() {
        State s = new State();
        s.categories.add(new Category("food", "Ăn uống", Type.EXPENSE));
        s.categories.add(new Category("transport", "Đi lại", Type.EXPENSE));
        s.categories.add(new Category("shopping", "Mua sắm", Type.EXPENSE));
        s.categories.add(new Category("bills", "Hóa đơn", Type.EXPENSE));
        s.categories.add(new Category("other-expense", "Khác", Type.EXPENSE));
        s.categories.add(new Category("salary", "Lương", Type.INCOME));
        s.categories.add(new Category("bonus", "Thưởng", Type.INCOME));
        s.categories.add(new Category("other-income", "Thu nhập khác", Type.INCOME));
        return s;
    }

    private final State state;
    private boolean restoring = true;

    // Constructor kiểm tra tính hợp lệ của toàn bộ dữ liệu đầu vào khi tải trạng thái
    public FinanceBook(State state) {
        if (state == null || state.version != 1 || state.wallets == null || state.categories == null
                || state.transactions == null || state.budgets == null) throw new Failure(Problem.STORAGE);
        this.state = new State();
        this.state.timeZone = state.timeZone == null ? "Asia/Ho_Chi_Minh" : state.timeZone;
        // java.time needs API 26 (minSdk 24); TimeZone silently falls back to GMT, so check the ID explicitly.
        if (!java.util.Arrays.asList(TimeZone.getAvailableIDs()).contains(this.state.timeZone)) throw new Failure(Problem.STORAGE);
        this.state.serverSnapshot = state.serverSnapshot;
        java.util.Set<String> walletIds = new java.util.HashSet<>(), categoryIds = new java.util.HashSet<>();

        // Kiểm tra tính hợp lệ của từng ví
        for (Wallet wallet : state.wallets) {
            if (wallet == null || wallet.id == null || wallet.id.isEmpty() || !walletIds.add(wallet.id)
                    || wallet.walletType == null || wallet.openingBalance < 0 || wallet.openingBalance > MAX_AMOUNT)
                throw new Failure(Problem.STORAGE);
            validName(wallet.name); this.state.wallets.add(wallet);
        }

        // Kiểm tra tính hợp lệ của từng danh mục
        for (Category category : state.categories) {
            if (category == null || category.id == null || category.id.isEmpty() || !categoryIds.add(category.id)
                    || category.type == null || category.type == Type.TRANSFER) throw new Failure(Problem.STORAGE);
            validName(category.name); this.state.categories.add(category);
        }

        for (Transaction transaction : state.transactions) save(transaction, false);
        for (Budget budget : state.budgets) save(budget, false);
        restoring = false;
        validateTotals();
        for (Wallet wallet : this.state.wallets) if (balance(wallet.id) < 0) throw new Failure(Problem.INSUFFICIENT_FUNDS);
    }

    public State state() { return state; }

    // Kiểm tra tổng số liệu toàn bộ sổ để đảm bảo không bị tràn số
    public void validateTotals() {
        long magnitude = 0;
        for (Wallet wallet : state.wallets) magnitude = Math.addExact(magnitude, wallet.openingBalance);
        for (Transaction transaction : state.transactions) magnitude = Math.addExact(magnitude, transaction.amount);
        long limits = 0;
        for (Budget budget : state.budgets) limits = Math.addExact(limits, budget.limit);
    }

    // Tìm kiếm Ví theo ID
    public Wallet wallet(String id) {
        for (Wallet item : state.wallets) if (item.id.equals(id)) return item;
        return null;
    }

    // Tìm kiếm Danh mục theo ID
    public Category category(String id) {
        for (Category item : state.categories) if (item.id.equals(id)) return item;
        return null;
    }

    // Tìm kiếm Giao dịch theo ID
    public Transaction transaction(String id) {
        for (Transaction item : state.transactions) if (item.id.equals(id)) return item;
        return null;
    }

    // Tìm kiếm Ngân sách theo ID
    public Budget budget(String id) {
        for (Budget item : state.budgets) if (item.id.equals(id)) return item;
        return null;
    }

    // Thêm mới hoặc cập nhật một Giao dịch (validate kỹ lưỡng trước khi lưu)
    public void save(Transaction value, boolean editing) {
        if (value == null || value.id == null || value.id.isEmpty() || value.type == null) throw new Failure(Problem.NOT_FOUND);
        amount(value.amount);
        if (value.occurredAt < 0 || value.occurredAt > 253402214400000L) throw new Failure(Problem.DATE);
        if (wallet(value.walletId) == null) throw new Failure(Problem.WALLET);
        if (!restoring && wallet(value.walletId).archivedAt != null) throw new Failure(Problem.ARCHIVED);

        // Xử lý riêng nếu là giao dịch chuyển tiền giữa các ví
        if (value.type == Type.TRANSFER) {
            if (wallet(value.destinationWalletId) == null) throw new Failure(Problem.WALLET);
            if (value.walletId.equals(value.destinationWalletId)) throw new Failure(Problem.SAME_WALLET);
            value.categoryId = null;
        } else {
            Category category = category(value.categoryId);
            if (category == null || category.type != value.type) throw new Failure(Problem.CATEGORY);
            if (!restoring && category.archivedAt != null) throw new Failure(Problem.ARCHIVED);
            value.destinationWalletId = null;
        }

        value.note = value.note == null ? "" : value.note.trim();
        value.payee = value.payee == null ? "" : value.payee.trim();
        if (value.note.length() > 2000 || value.payee.length() > 160) throw new Failure(Problem.NAME);

        Transaction old = transaction(value.id);
        if (editing && old == null) throw new Failure(Problem.NOT_FOUND);
        if (!editing && old != null) throw new Failure(Problem.NOT_FOUND);
        if (!restoring && old != null && "VOIDED".equals(old.status)) throw new Failure(Problem.VOIDED);
        if (!restoring) checkContributions(old, value);
        if (old != null) state.transactions.remove(old);
        state.transactions.add(value);
    }

    // Thêm mới hoặc cập nhật một Khoản ngân sách
    public void save(Budget value, boolean editing) {
        if (value == null || value.id == null || value.id.isEmpty()) throw new Failure(Problem.NOT_FOUND);
        amount(value.limit);
        validateMonth(value.month);
        Category category = category(value.categoryId);
        if (category == null || category.type != Type.EXPENSE) throw new Failure(Problem.CATEGORY);
        Budget old = budget(value.id);
        if ((editing && old == null) || (!editing && old != null)) throw new Failure(Problem.NOT_FOUND);
        if (!restoring && old != null && old.archivedAt != null) throw new Failure(Problem.ARCHIVED);
        if (!restoring && category.archivedAt != null && (old == null || !old.categoryId.equals(value.categoryId)))
            throw new Failure(Problem.ARCHIVED);

        // Kiểm tra tránh trùng lặp ngân sách cùng danh mục trong một tháng
        for (Budget item : state.budgets) {
            if (item.archivedAt == null && value.archivedAt == null && !item.id.equals(value.id) && item.categoryId.equals(value.categoryId) && item.month.equals(value.month))
                throw new Failure(Problem.DUPLICATE_BUDGET);
        }
        if (old != null) state.budgets.remove(old);
        state.budgets.add(value);
    }

    // Xóa một giao dịch theo ID
    public void deleteTransaction(String id) {
        Transaction old = transaction(id);
        if (old == null) throw new Failure(Problem.NOT_FOUND);
        if ("VOIDED".equals(old.status)) return;
        checkContributions(old, null);
        old.status = "VOIDED";
        old.voidedAt = now();
    }

    // Xóa một ngân sách theo ID
    public void deleteBudget(String id) {
        Budget old = budget(id);
        if (old == null) throw new Failure(Problem.NOT_FOUND);
        if (old.archivedAt == null) old.archivedAt = now();
    }

    // Thêm ví mới với loại mặc định là tiền mặt (CASH)
    public Wallet addWallet(String name, long openingBalance) {
        return addWallet(name, openingBalance, WalletType.CASH);
    }

    // Thêm ví mới kèm loại ví tùy chỉnh
    public Wallet addWallet(String name, long openingBalance, WalletType type) {
        name = validName(name);
        if (openingBalance < 0 || openingBalance > MAX_AMOUNT) throw new Failure(Problem.AMOUNT);
        for (Wallet item : state.wallets) if (item.archivedAt == null && sameName(item.name, name)) throw new Failure(Problem.NAME);
        Wallet wallet = new Wallet(UUID.randomUUID().toString(), name, openingBalance);
        if (type == null) throw new Failure(Problem.WALLET);
        wallet.walletType = type;
        state.wallets.add(wallet);
        return wallet;
    }

    // Thêm một danh mục mới
    public Category addCategory(String name, Type type) {
        name = validName(name);
        if (type == null || type == Type.TRANSFER) throw new Failure(Problem.CATEGORY);
        for (Category item : state.categories) if (item.archivedAt == null && item.type == type && sameName(item.name, name)) throw new Failure(Problem.NAME);
        Category category = new Category(UUID.randomUUID().toString(), name, type);
        state.categories.add(category);
        return category;
    }

    // Kiểm tra tính hợp lệ của tên (không được để trống, tối đa 100 ký tự)
    private static String validName(String name) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 100) throw new Failure(Problem.NAME);
        return Normalizer.normalize(name.trim(), Normalizer.Form.NFC);
    }

    // Phân tích và chuyển đổi chuỗi tiền tệ sang số nguyên (long)
    public static long parseAmount(String text) {
        if (text == null || !text.matches("[0-9]{1,12}|[0-9]{1,3}(\\.[0-9]{3}){1,3}")) throw new Failure(Problem.AMOUNT);
        try { long value = Long.parseLong(text.replace(".", "")); amount(value); return value; }
        catch (NumberFormatException e) { throw new Failure(Problem.AMOUNT); }
    }

    // Kiểm tra số tiền nằm trong khoảng hợp lệ (> 0 và <= MAX_AMOUNT)
    private static void amount(long value) { if (value <= 0 || value > MAX_AMOUNT) throw new Failure(Problem.AMOUNT); }

    // Tính toán số dư hiện tại của một ví dựa trên số dư ban đầu cộng/trừ các giao dịch
    public long balance(String walletId) {
        Wallet wallet = wallet(walletId);
        if (wallet == null) throw new Failure(Problem.WALLET);
        if (wallet.serverBalanceMinor != null) return wallet.serverBalanceMinor;
        long result = wallet.openingBalance;
        for (Transaction item : state.transactions) {
            if ("VOIDED".equals(item.status)) continue;
            if (walletId.equals(item.walletId)) result = Math.addExact(result, item.type == Type.INCOME ? item.amount : -item.amount);
            if (item.type == Type.TRANSFER && walletId.equals(item.destinationWalletId)) result = Math.addExact(result, item.amount);
        }
        return result;
    }

    // Tính tổng số tiền thu/chi theo tháng
    public long total(Type type, String month) {
        long total = 0;
        for (Transaction item : transactions(month, type, "", null, null)) total = Math.addExact(total, item.amount);
        return total;
    }

    // Tính tổng tài sản (có thể lọc chỉ tính riêng ví tiết kiệm savingsOnly = true)
    public long assets(boolean savingsOnly) {
        long result = 0;
        for (Wallet wallet : state.wallets) if (wallet.archivedAt == null && wallet.includeInAssets && (!savingsOnly || wallet.walletType == WalletType.SAVINGS))
            result = Math.addExact(result, balance(wallet.id));
        return result;
    }

    // Tính tổng số tiền đã chi tiêu cho một khoản ngân sách cụ thể
    public long spent(Budget budget) {
        if (budget.serverSpentMinor != null) return budget.serverSpentMinor;
        long total = 0;
        for (Transaction item : transactions(budget.month, Type.EXPENSE, "", null, budget.categoryId)) total = Math.addExact(total, item.amount);
        return total;
    }

    // Lớp chứa thông tin thống kê trạng thái sử dụng ngân sách
    public static final class BudgetUsage {
        public final long spent, remaining;
        public final String percentage;
        public final int progress;
        public final boolean warning, exceeded;
        BudgetUsage(long spent, long limit, int threshold) {
            this.spent = spent; remaining = Math.subtractExact(limit, spent);
            java.math.BigInteger percent = java.math.BigInteger.valueOf(spent).multiply(java.math.BigInteger.valueOf(100))
                    .divide(java.math.BigInteger.valueOf(limit));
            percentage = percent.toString(); progress = percent.min(java.math.BigInteger.valueOf(100)).intValue();
            warning = java.math.BigInteger.valueOf(spent).multiply(java.math.BigInteger.valueOf(100))
                    .compareTo(java.math.BigInteger.valueOf(limit).multiply(java.math.BigInteger.valueOf(threshold))) >= 0;
            exceeded = spent >= limit;
        }
    }

    // Lấy thông tin chi tiết mức độ sử dụng của một ngân sách
    public BudgetUsage usage(Budget budget) { return new BudgetUsage(spent(budget), budget.limit, budget.warningThresholdPercent); }

    // Tổng hạn mức ngân sách trong một tháng
    public long budgetLimit(String month) {
        long result = 0; for (Budget item : budgets(month)) result = Math.addExact(result, item.limit); return result;
    }

    // Tổng số tiền đã chi tiêu theo ngân sách trong một tháng
    public long budgetSpent(String month) {
        long result = 0; for (Budget item : budgets(month)) result = Math.addExact(result, spent(item)); return result;
    }

    // Lấy danh sách tất cả các ngân sách trong một tháng cụ thể
    public List<Budget> budgets(String month) {
        validateMonth(month);
        List<Budget> result = new ArrayList<>();
        for (Budget item : state.budgets) if (item.archivedAt == null && item.month.equals(month)) result.add(item);
        return Collections.unmodifiableList(result);
    }

    // Tìm kiếm và lọc danh sách giao dịch theo tháng, loại, từ khóa, ví hoặc danh mục
    public List<Transaction> transactions(String month, Type type, String query, String walletId, String categoryId) {
        validateMonth(month);
        String search = fold(query == null ? "" : query.trim());
        List<Transaction> result = new ArrayList<>();
        for (Transaction item : state.transactions) {
            if ("VOIDED".equals(item.status) || !monthAt(item.occurredAt).equals(month) || (type != null && item.type != type)) continue;
            if (walletId != null && !walletId.equals(item.walletId) && !walletId.equals(item.destinationWalletId)) continue;
            if (categoryId != null && !categoryId.equals(item.categoryId)) continue;
            Category category = category(item.categoryId);
            Wallet wallet = wallet(item.walletId), destination = wallet(item.destinationWalletId);

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

    /** Server-side history filter (contract section 5). Dates are inclusive YYYY-MM-DD in the user's timezone. */
    public static final class Query {
        public final String dateFrom, dateTo, walletId, categoryId, q, sort;
        public final Type type;
        public Query(String dateFrom, String dateTo, Type type, String walletId, String categoryId, String q, String sort) {
            this.dateFrom = dateFrom; this.dateTo = dateTo; this.type = type; this.walletId = walletId; this.categoryId = categoryId;
            String text = q == null ? "" : q.trim();
            this.q = text.isEmpty() ? null : text.length() > 200 ? text.substring(0, 200) : text;
            this.sort = sort == null ? "occurredAt,desc" : sort;
        }
        public static Query month(String month, Type type, String walletId, String categoryId, String q) {
            validateMonth(month);
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC")); c.clear();
            c.set(Integer.parseInt(month.substring(0, 4)), Integer.parseInt(month.substring(5)) - 1, 1);
            String last = String.format(Locale.ROOT, "%s-%02d", month, c.getActualMaximum(Calendar.DAY_OF_MONTH));
            return new Query(month + "-01", last, type, walletId, categoryId, q, null);
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Query)) return false;
            Query o = (Query) other;
            return java.util.Objects.equals(dateFrom, o.dateFrom) && java.util.Objects.equals(dateTo, o.dateTo) && type == o.type
                    && java.util.Objects.equals(walletId, o.walletId) && java.util.Objects.equals(categoryId, o.categoryId)
                    && java.util.Objects.equals(q, o.q) && sort.equals(o.sort);
        }
        @Override public int hashCode() { return java.util.Objects.hash(dateFrom, dateTo, type, walletId, categoryId, q, sort); }
    }

    public static final class Page {
        public final List<Transaction> items;
        public final int page, totalPages;
        public Page(List<Transaction> items, int page, int totalPages) { this.items = items; this.page = page; this.totalPages = totalPages; }
    }

    /** Mock implementation of GET /transactions: POSTED only, literal case-insensitive note/payee search, allowlisted sort. */
    public Page query(Query query, int page, int size) {
        java.text.SimpleDateFormat days = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        days.setTimeZone(TimeZone.getTimeZone(state.timeZone));
        String search = query.q == null ? null : query.q.toLowerCase(Locale.ROOT);
        List<Transaction> matches = new ArrayList<>();
        for (Transaction item : state.transactions) {
            if ("VOIDED".equals(item.status) || (query.type != null && item.type != query.type)) continue;
            String day = days.format(new java.util.Date(item.occurredAt));
            if ((query.dateFrom != null && day.compareTo(query.dateFrom) < 0) || (query.dateTo != null && day.compareTo(query.dateTo) > 0)) continue;
            if (query.walletId != null && !query.walletId.equals(item.walletId)) continue;
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

    /** Appends a later page, skipping IDs already shown (pages may shift under concurrent writes). */
    public static List<Transaction> merge(List<Transaction> rows, List<Transaction> next) {
        List<Transaction> result = new ArrayList<>(rows);
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Transaction item : rows) ids.add(item.id);
        for (Transaction item : next) if (ids.add(item.id)) result.add(item);
        return Collections.unmodifiableList(result);
    }

    // Chuyển đổi timestamp thành định dạng tháng (YYYY-MM)
    public static String month(long timestamp) { return month(timestamp, ZONE); }

    private static String month(long timestamp, TimeZone zone) {
        Calendar c = Calendar.getInstance(zone); c.setTimeInMillis(timestamp);
        return String.format(Locale.ROOT, "%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
    }

    public String monthAt(long timestamp) { return month(timestamp, TimeZone.getTimeZone(state.timeZone)); }

    /** UTC ISO-8601 timestamp without java.time (API 26+). */
    private static String now() {
        java.text.SimpleDateFormat utc = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        utc.setTimeZone(TimeZone.getTimeZone("UTC"));
        return utc.format(new java.util.Date());
    }

    // Kiểm tra định dạng tháng hợp lệ (YYYY-MM)
    public static void validateMonth(String month) {
        if (month == null || !month.matches("[0-9]{4}-(0[1-9]|1[0-2])")
                || Integer.parseInt(month.substring(0, 4)) < 1 || Integer.parseInt(month.substring(0, 4)) > 9998)
            throw new Failure(Problem.DATE);
    }

    private void checkContributions(Transaction old, Transaction value) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        if (old != null) { ids.add(old.walletId); if (old.destinationWalletId != null) ids.add(old.destinationWalletId); }
        if (value != null) { ids.add(value.walletId); if (value.destinationWalletId != null) ids.add(value.destinationWalletId); }
        for (String id : ids) {
            long candidate = Math.addExact(Math.subtractExact(balance(id), contribution(old, id)), contribution(value, id));
            if (candidate < 0) throw new Failure(Problem.INSUFFICIENT_FUNDS);
        }
    }

    private static long contribution(Transaction value, String id) {
        if (value == null || "VOIDED".equals(value.status)) return 0;
        long delta = id.equals(value.walletId) ? (value.type == Type.INCOME ? value.amount : -value.amount) : 0;
        if (value.type == Type.TRANSFER && id.equals(value.destinationWalletId)) delta = Math.addExact(delta, value.amount);
        return delta;
    }

    private static boolean sameName(String first, String second) {
        return Normalizer.normalize(first, Normalizer.Form.NFC).equalsIgnoreCase(Normalizer.normalize(second, Normalizer.Form.NFC));
    }

    public void updateWallet(String id, String name, long opening, WalletType type, boolean included, int order) {
        Wallet old = wallet(id);
        if (old == null) throw new Failure(Problem.NOT_FOUND);
        if (old.archivedAt != null) throw new Failure(Problem.ARCHIVED);
        name = validName(name);
        if (opening < 0 || opening > MAX_AMOUNT || type == null || order < 0) throw new Failure(Problem.AMOUNT);
        for (Wallet item : state.wallets) if (!id.equals(item.id) && item.archivedAt == null && sameName(item.name, name))
            throw new Failure(Problem.NAME);
        if (Math.addExact(Math.subtractExact(balance(id), old.openingBalance), opening) < 0)
            throw new Failure(Problem.INSUFFICIENT_FUNDS);
        old.name = name; old.openingBalance = opening; old.walletType = type; old.includeInAssets = included; old.sortOrder = order;
    }

    public void walletStatus(String id, boolean active) {
        Wallet item = wallet(id);
        if (item == null) throw new Failure(Problem.NOT_FOUND);
        if (active) {
            for (Wallet other : state.wallets) if (!id.equals(other.id) && other.archivedAt == null && sameName(other.name, item.name))
                throw new Failure(Problem.NAME);
            item.archivedAt = null;
        } else if (item.archivedAt == null) item.archivedAt = now();
    }

    public void updateCategory(String id, String name, Type type, String icon, String color, int order) {
        Category old = category(id);
        if (old == null) throw new Failure(Problem.NOT_FOUND);
        if (old.archivedAt != null) throw new Failure(Problem.ARCHIVED);
        name = validName(name);
        if (type == null || type == Type.TRANSFER || order < 0) throw new Failure(Problem.CATEGORY);
        if (old.type != type) {
            for (Transaction transaction : state.transactions) if (id.equals(transaction.categoryId)) throw new Failure(Problem.CONFLICT);
            for (Budget budget : state.budgets) if (id.equals(budget.categoryId)) throw new Failure(Problem.CONFLICT);
        }
        for (Category item : state.categories) if (!id.equals(item.id) && item.archivedAt == null && item.type == type && sameName(item.name, name))
            throw new Failure(Problem.NAME);
        old.name = name; old.type = type; old.iconKey = icon; old.colorHex = color; old.sortOrder = order;
    }

    public void categoryStatus(String id, boolean active) {
        Category item = category(id);
        if (item == null) throw new Failure(Problem.NOT_FOUND);
        if (active) {
            for (Category other : state.categories) if (!id.equals(other.id) && other.archivedAt == null && other.type == item.type && sameName(other.name, item.name))
                throw new Failure(Problem.NAME);
            item.archivedAt = null;
        } else if (item.archivedAt == null) item.archivedAt = now();
    }

    // Chuẩn hóa chuỗi văn bản (bỏ dấu tiếng Việt, chuyển chữ thường) để hỗ trợ tìm kiếm không dấu
    private static String fold(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
