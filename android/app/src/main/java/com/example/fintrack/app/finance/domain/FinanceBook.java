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
        NETWORK, UNAUTHORIZED, CONFLICT, VALIDATION, SERVER }

    // Ngoại lệ dùng chung trong toàn bộ nghiệp vụ tài chính
    public static final class Failure extends RuntimeException {
        public final Problem problem;
        public Failure(Problem problem) { super(problem.name()); this.problem = problem; }
    }

    // Mô hình dữ liệu Ví tiền
    public static final class Wallet {
        public String id, name;
        public long openingBalance;
        public WalletType walletType = WalletType.CASH;
        public Wallet(String id, String name, long openingBalance) {
            this.id = id; this.name = name; this.openingBalance = openingBalance;
        }
    }

    // Mô hình dữ liệu Danh mục thu/chi
    public static final class Category {
        public String id, name;
        public Type type;
        public Category(String id, String name, Type type) { this.id = id; this.name = name; this.type = type; }
    }

    // Mô hình dữ liệu Giao dịch tài chính
    public static final class Transaction {
        public String id, categoryId, walletId, destinationWalletId, note, payee;
        public Type type;
        public long amount, occurredAt;
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
        public Budget(String id, String categoryId, String month, long limit) {
            this.id = id; this.categoryId = categoryId; this.month = month; this.limit = limit;
        }
    }

    /** Trạng thái lưu trữ dữ liệu (State), có phiên bản để tránh ghi đè dữ liệu bị lỗi/hỏng. */
    public static final class State {
        public int version = 1;
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
        s.categories.add(new Category("other-expense", "Chi tiêu khác", Type.EXPENSE));
        s.categories.add(new Category("salary", "Lương", Type.INCOME));
        s.categories.add(new Category("other-income", "Thu nhập khác", Type.INCOME));
        return s;
    }

    private final State state;

    // Constructor kiểm tra tính hợp lệ của toàn bộ dữ liệu đầu vào khi tải trạng thái
    public FinanceBook(State state) {
        if (state == null || state.version != 1 || state.wallets == null || state.categories == null
                || state.transactions == null || state.budgets == null) throw new Failure(Problem.STORAGE);
        this.state = new State();
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
        validateTotals();
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

        // Xử lý riêng nếu là giao dịch chuyển tiền giữa các ví
        if (value.type == Type.TRANSFER) {
            if (wallet(value.destinationWalletId) == null) throw new Failure(Problem.WALLET);
            if (value.walletId.equals(value.destinationWalletId)) throw new Failure(Problem.SAME_WALLET);
            value.categoryId = null;
        } else {
            Category category = category(value.categoryId);
            if (category == null || category.type != value.type) throw new Failure(Problem.CATEGORY);
            value.destinationWalletId = null;
        }

        value.note = value.note == null ? "" : value.note.trim();
        value.payee = value.payee == null ? "" : value.payee.trim();
        if (value.note.length() > 2000 || value.payee.length() > 160) throw new Failure(Problem.NAME);

        Transaction old = transaction(value.id);
        if (editing && old == null) throw new Failure(Problem.NOT_FOUND);
        if (!editing && old != null) throw new Failure(Problem.NOT_FOUND);
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

        // Kiểm tra tránh trùng lặp ngân sách cùng danh mục trong một tháng
        for (Budget item : state.budgets) {
            if (!item.id.equals(value.id) && item.categoryId.equals(value.categoryId) && item.month.equals(value.month))
                throw new Failure(Problem.DUPLICATE_BUDGET);
        }
        if (old != null) state.budgets.remove(old);
        state.budgets.add(value);
    }

    // Xóa một giao dịch theo ID
    public void deleteTransaction(String id) {
        Transaction old = transaction(id);
        if (old == null) throw new Failure(Problem.NOT_FOUND);
        state.transactions.remove(old);
    }

    // Xóa một ngân sách theo ID
    public void deleteBudget(String id) {
        Budget old = budget(id);
        if (old == null) throw new Failure(Problem.NOT_FOUND);
        state.budgets.remove(old);
    }

    // Thêm ví mới với loại mặc định là tiền mặt (CASH)
    public Wallet addWallet(String name, long openingBalance) {
        return addWallet(name, openingBalance, WalletType.CASH);
    }

    // Thêm ví mới kèm loại ví tùy chỉnh
    public Wallet addWallet(String name, long openingBalance, WalletType type) {
        name = validName(name);
        if (openingBalance < 0 || openingBalance > MAX_AMOUNT) throw new Failure(Problem.AMOUNT);
        for (Wallet item : state.wallets) if (fold(item.name).equals(fold(name))) throw new Failure(Problem.NAME);
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
        for (Category item : state.categories) if (item.type == type && fold(item.name).equals(fold(name))) throw new Failure(Problem.NAME);
        Category category = new Category(UUID.randomUUID().toString(), name, type);
        state.categories.add(category);
        return category;
    }

    // Kiểm tra tính hợp lệ của tên (không được để trống, tối đa 100 ký tự)
    private static String validName(String name) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 100) throw new Failure(Problem.NAME);
        return name.trim();
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
        long result = wallet.openingBalance;
        for (Transaction item : state.transactions) {
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
        for (Wallet wallet : state.wallets) if (!savingsOnly || wallet.walletType == WalletType.SAVINGS)
            result = Math.addExact(result, balance(wallet.id));
        return result;
    }

    // Tính tổng số tiền đã chi tiêu cho một khoản ngân sách cụ thể
    public long spent(Budget budget) {
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
        BudgetUsage(long spent, long limit) {
            this.spent = spent; remaining = Math.subtractExact(limit, spent);
            java.math.BigInteger percent = java.math.BigInteger.valueOf(spent).multiply(java.math.BigInteger.valueOf(100))
                    .divide(java.math.BigInteger.valueOf(limit));
            percentage = percent.toString(); progress = percent.min(java.math.BigInteger.valueOf(100)).intValue();
            warning = percent.compareTo(java.math.BigInteger.valueOf(80)) >= 0; exceeded = spent >= limit;
        }
    }

    // Lấy thông tin chi tiết mức độ sử dụng của một ngân sách
    public BudgetUsage usage(Budget budget) { return new BudgetUsage(spent(budget), budget.limit); }

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
        for (Budget item : state.budgets) if (item.month.equals(month)) result.add(item);
        return Collections.unmodifiableList(result);
    }

    // Tìm kiếm và lọc danh sách giao dịch theo tháng, loại, từ khóa, ví hoặc danh mục
    public List<Transaction> transactions(String month, Type type, String query, String walletId, String categoryId) {
        validateMonth(month);
        String search = fold(query == null ? "" : query.trim());
        List<Transaction> result = new ArrayList<>();
        for (Transaction item : state.transactions) {
            if (!month(item.occurredAt).equals(month) || (type != null && item.type != type)) continue;
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

    // Chuyển đổi timestamp thành định dạng tháng (YYYY-MM)
    public static String month(long timestamp) {
        Calendar c = Calendar.getInstance(ZONE); c.setTimeInMillis(timestamp);
        return String.format(Locale.ROOT, "%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
    }

    // Kiểm tra định dạng tháng hợp lệ (YYYY-MM)
    public static void validateMonth(String month) {
        if (month == null || !month.matches("[1-9][0-9]{3}-(0[1-9]|1[0-2])")) throw new Failure(Problem.DATE);
    }

    // Chuẩn hóa chuỗi văn bản (bỏ dấu tiếng Việt, chuyển chữ thường) để hỗ trợ tìm kiếm không dấu
    private static String fold(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}