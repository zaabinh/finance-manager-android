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

    // Khởi tạo sổ tài chính mặc định với các danh mục cơ bản sẵn có
    public static FinanceSnapshot.Builder empty() {
        FinanceSnapshot.Builder s = new FinanceSnapshot.Builder();
        s.categories.add(new Category("food", "Ăn uống", TransactionType.EXPENSE));
        s.categories.add(new Category("transport", "Đi lại", TransactionType.EXPENSE));
        s.categories.add(new Category("shopping", "Mua sắm", TransactionType.EXPENSE));
        s.categories.add(new Category("bills", "Hóa đơn", TransactionType.EXPENSE));
        s.categories.add(new Category("other-expense", "Khác", TransactionType.EXPENSE));
        s.categories.add(new Category("salary", "Lương", TransactionType.INCOME));
        s.categories.add(new Category("bonus", "Thưởng", TransactionType.INCOME));
        s.categories.add(new Category("other-income", "Thu nhập khác", TransactionType.INCOME));
        // Same icon keys as the backend seed (V8__category_seed.sql), in the order added above.
        String[] icons = {"coffee", "car", "shopping_bag", "receipt", "more_horiz", "briefcase", "gift", "more_horiz"};
        for (int i = 0; i < icons.length; i++) {
            Category c = s.categories.get(i);
            s.categories.set(i, new Category(c.id,c.name,c.type,c.archivedAt,icons[i],c.colorHex,c.sortOrder));
        }
        return s;
    }

    private final FinanceSnapshot.Builder state;
    private boolean restoring = true;

    // Constructor kiểm tra tính hợp lệ của toàn bộ dữ liệu đầu vào khi tải trạng thái
    public FinanceBook(FinanceSnapshot.Builder state) { this(state.build()); }

    public FinanceBook(FinanceSnapshot state) {
        String timeZone = LedgerRestorer.validate(state);
        this.state = new FinanceSnapshot.Builder();
        this.state.timeZone = timeZone;
        this.state.serverSnapshot = state.serverSnapshot;
        this.state.wallets.addAll(state.wallets);
        this.state.categories.addAll(state.categories);
        if (state.monthlyBudgets != null) this.state.monthlyBudgets.addAll(state.monthlyBudgets);
        for (Transaction transaction : state.transactions) save(transaction, false);
        for (Budget budget : state.budgets) save(budget, false);
        restoring = false;
        validateTotals();
        for (Wallet wallet : this.state.wallets) if (balance(wallet.id) < 0) throw new FinanceFailure(FinanceProblem.INSUFFICIENT_FUNDS);
    }

    public FinanceSnapshot snapshot() { return state.build(); }

    public List<Wallet> wallets() { return Collections.unmodifiableList(state.wallets); }
    public List<Category> categories() { return Collections.unmodifiableList(state.categories); }
    public List<Transaction> transactions() { return Collections.unmodifiableList(state.transactions); }
    public List<Budget> budgets() { return Collections.unmodifiableList(state.budgets); }
    public String timeZone() { return state.timeZone; }

    // Kiểm tra tổng số liệu toàn bộ sổ để đảm bảo không bị tràn số
    public void validateTotals() {
        LedgerCalculations.validateTotals(wallets(), transactions(), budgets());
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
        value = TransactionPolicy.normalize(value, value == null ? null : wallet(value.walletId),
                value == null ? null : wallet(value.destinationWalletId), value == null ? null : category(value.categoryId), restoring);
        Transaction old = transaction(value.id);
        TransactionPolicy.validateReplacement(old, editing, restoring);
        if (!restoring) checkContributions(old, value);
        if (old != null) state.transactions.remove(old);
        state.transactions.add(value);
    }

    // Thêm mới hoặc cập nhật một Khoản ngân sách
    public void save(Budget value, boolean editing) {
        Budget old = value == null ? null : budget(value.id);
        BudgetPolicy.validateCategoryBudget(value, value == null ? null : category(value.categoryId),
                value == null || value.monthlyBudgetId == null ? null : monthlyBudget(value.monthlyBudgetId),
                old, budgets(), editing, restoring);
        if (old != null) state.budgets.remove(old);
        state.budgets.add(value);
    }

    // Xóa một giao dịch theo ID
    public void deleteTransaction(String id) {
        Transaction old = transaction(id);
        if (old == null) throw new FinanceFailure(FinanceProblem.NOT_FOUND);
        if ("VOIDED".equals(old.status)) return;
        checkContributions(old, null);
        state.transactions.set(state.transactions.indexOf(old), new Transaction(old.id,old.type,old.amount,
                old.categoryId,old.walletId,old.destinationWalletId,old.occurredAt,old.note,old.payee,"VOIDED",now(),old.clientRequestId));
    }

    // Xóa một ngân sách theo ID
    public void deleteBudget(String id) {
        Budget old = budget(id);
        if (old == null) throw new FinanceFailure(FinanceProblem.NOT_FOUND);
        if (old.archivedAt == null) state.budgets.set(state.budgets.indexOf(old), old.withArchivedAt(now()));
    }

    // Thêm ví mới với loại mặc định là tiền mặt (CASH)
    public Wallet addWallet(String name, long openingBalance) {
        return addWallet(name, openingBalance, WalletType.CASH);
    }

    // Thêm ví mới kèm loại ví tùy chỉnh
    public Wallet addWallet(String name, long openingBalance, WalletType type) {
        return addWallet(name, openingBalance, type, true, 0);
    }

    public Wallet addWallet(String name, long openingBalance, WalletType type, boolean included, int order) {
        name = validName(name);
        if (openingBalance < 0 || openingBalance > MAX_AMOUNT) throw new FinanceFailure(FinanceProblem.AMOUNT);
        for (Wallet item : state.wallets) if (item.archivedAt == null && sameName(item.name, name)) throw new FinanceFailure(FinanceProblem.NAME);
        if (type == null) throw new FinanceFailure(FinanceProblem.WALLET);
        Wallet wallet = new Wallet(UUID.randomUUID().toString(), name, openingBalance, type, null, included, order, null);
        state.wallets.add(wallet);
        return wallet;
    }

    // Thêm một danh mục mới
    public Category addCategory(String name, TransactionType type) {
        return addCategory(name, type, null, null, 0);
    }

    public Category addCategory(String name, TransactionType type, String icon, String color, int order) {
        name = validName(name);
        if (type == null || type == TransactionType.TRANSFER) throw new FinanceFailure(FinanceProblem.CATEGORY);
        for (Category item : state.categories) if (item.archivedAt == null && item.type == type && sameName(item.name, name)) throw new FinanceFailure(FinanceProblem.NAME);
        Category category = new Category(UUID.randomUUID().toString(), name, type, null, icon, color, order);
        state.categories.add(category);
        return category;
    }

    // Kiểm tra tính hợp lệ của tên (không được để trống, tối đa 100 ký tự)
    static String validName(String name) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 100) throw new FinanceFailure(FinanceProblem.NAME);
        return Normalizer.normalize(name.trim(), Normalizer.Form.NFC);
    }

    // Phân tích và chuyển đổi chuỗi tiền tệ sang số nguyên (long)
    public static long parseAmount(String text) {
        if (text == null || !text.matches("[0-9]{1,12}|[0-9]{1,3}(\\.[0-9]{3}){1,3}")) throw new FinanceFailure(FinanceProblem.AMOUNT);
        try { long value = Long.parseLong(text.replace(".", "")); amount(value); return value; }
        catch (NumberFormatException e) { throw new FinanceFailure(FinanceProblem.AMOUNT); }
    }

    // Kiểm tra số tiền nằm trong khoảng hợp lệ (> 0 và <= MAX_AMOUNT)
    static void amount(long value) { if (value <= 0 || value > MAX_AMOUNT) throw new FinanceFailure(FinanceProblem.AMOUNT); }

    // Tính toán số dư hiện tại của một ví dựa trên số dư ban đầu cộng/trừ các giao dịch
    public long balance(String walletId) { return LedgerCalculations.balance(this, state, walletId); }

    // Tính tổng số tiền thu/chi theo tháng
    public long total(TransactionType type, String month) { return LedgerCalculations.total(this, state, type, month); }

    // Tính tổng tài sản (có thể lọc chỉ tính riêng ví tiết kiệm savingsOnly = true)
    public long assets(boolean savingsOnly) { return LedgerCalculations.assets(this, state, savingsOnly); }

    // Tính tổng số tiền đã chi tiêu cho một khoản ngân sách cụ thể
    public long spent(Budget budget) { return LedgerCalculations.spent(this, state, budget); }

    // Lấy thông tin chi tiết mức độ sử dụng của một ngân sách
    public BudgetUsage usage(Budget budget) { return new BudgetUsage(spent(budget), budget.limit, budget.warningThresholdPercent); }

    // Tổng hạn mức ngân sách trong một tháng
    public long budgetLimit(String month) { return LedgerCalculations.budgetLimit(this, state, month); }

    // Tổng số tiền đã chi tiêu theo ngân sách trong một tháng
    public long budgetSpent(String month) { return LedgerCalculations.budgetSpent(this, state, month); }

    // Lấy danh sách tất cả các ngân sách trong một tháng cụ thể
    public List<Budget> budgets(String month) {
        validateMonth(month);
        List<Budget> result = new ArrayList<>();
        for (Budget item : state.budgets) if (item.archivedAt == null && item.month.equals(month)) result.add(item);
        return Collections.unmodifiableList(result);
    }

    // ===== Ngân sách tháng (hierarchical budgets, spec 2026-11-09 §1) =====

    public static final String BUDGET_MONTH_MISSING = "BUDGET_MONTH_MISSING";
    public static final String BUDGET_MONTH_CURRENT = "BUDGET_MONTH_CURRENT";
    public static final String BUDGET_EXCEEDS_AVAILABLE_BALANCE = "BUDGET_EXCEEDS_AVAILABLE_BALANCE";
    public static final String BUDGET_PERIOD_CONFLICT = "BUDGET_PERIOD_CONFLICT";
    public static final String BUDGET_ARCHIVED = "BUDGET_ARCHIVED";

    public MonthlyBudget monthlyBudget(String id) {
        for (MonthlyBudget item : state.monthlyBudgets) if (item.id.equals(id)) return item;
        return null;
    }

    /** The active monthly budget of a month, or null. */
    public MonthlyBudget monthlyBudgetFor(String month) {
        for (MonthlyBudget item : state.monthlyBudgets) if (item.archivedAt == null && item.month.equals(month)) return item;
        return null;
    }

    /** Active monthly budgets, newest month first. */
    public List<MonthlyBudget> monthlyBudgets() {
        List<MonthlyBudget> result = new ArrayList<>();
        for (MonthlyBudget item : state.monthlyBudgets) if (item.archivedAt == null) result.add(item);
        result.sort(Comparator.comparing((MonthlyBudget item) -> item.month).reversed().thenComparing(item -> item.id));
        return Collections.unmodifiableList(result);
    }

    /** Category budgets of a monthly budget in its lifecycle: active children, or those archived together with it. */
    public List<Budget> categoryBudgets(MonthlyBudget monthly) {
        List<Budget> result = new ArrayList<>();
        for (Budget item : state.budgets)
            if (monthly.id.equals(item.monthlyBudgetId) && java.util.Objects.equals(item.archivedAt, monthly.archivedAt)) result.add(item);
        result.sort(Comparator.comparing((Budget item) -> item.categoryId).thenComparing(item -> item.id));
        return Collections.unmodifiableList(result);
    }

    /** Derived total = sum of the category limits; the server value wins when present. */
    public long monthlyTotal(MonthlyBudget monthly) { return LedgerCalculations.monthlyTotal(this, state, monthly); }

    /** All POSTED EXPENSE of the month in any category; TRANSFER never counts. */
    public long monthlySpent(MonthlyBudget monthly) { return LedgerCalculations.monthlySpent(this, state, monthly); }

    public BudgetUsage monthlyUsage(MonthlyBudget monthly) {
        return new BudgetUsage(monthlySpent(monthly), monthlyTotal(monthly), monthly.warningThresholdPercent);
    }

    /** Spendable money: active wallets counted in assets, savings excluded. */
    public long spendable() { return LedgerCalculations.spendable(this, state); }

    /** Mock of POST /monthly-budgets: month + at least one category, validated fully before anything is written. */
    public MonthlyBudget createMonthlyBudget(String id, String month, List<Budget> categories) {
        validateMonth(month);
        if (id == null || id.isEmpty() || monthlyBudget(id) != null) throw new FinanceFailure(FinanceProblem.NOT_FOUND);
        long total = BudgetPolicy.allocation(categories, this::category);
        if (monthlyBudgetFor(month) != null) throw conflict(BUDGET_PERIOD_CONFLICT);
        requireBalance(month, 0, total);
        MonthlyBudget monthly = new MonthlyBudget(id, month);
        state.monthlyBudgets.add(monthly);
        try {
            for (Budget line : categories) {
                Budget value = new Budget(line.id == null ? UUID.randomUUID().toString() : line.id, line.categoryId, month, line.limit);
                value = value.withMonthlyBudgetId(id);
                save(value, false);
            }
        } catch (RuntimeException error) {
            // Keep the book unchanged on failure (atomic like the server transaction).
            state.monthlyBudgets.remove(monthly);
            for (java.util.Iterator<Budget> it = state.budgets.iterator(); it.hasNext(); ) if (id.equals(it.next().monthlyBudgetId)) it.remove();
            throw error;
        }
        return monthly;
    }

    /** Mock of POST/PUT /budgets under a monthly budget: parent resolved by month, balance block on increases. */
    public void saveCategoryBudget(Budget value, boolean editing) {
        if (value == null) throw new FinanceFailure(FinanceProblem.NOT_FOUND);
        validateMonth(value.month);
        MonthlyBudget target = monthlyBudgetFor(value.month);
        if (target == null) throw conflict(BUDGET_MONTH_MISSING);
        Budget old = budget(value.id);
        long before = monthlyTotal(target);
        long after = BudgetPolicy.categoryTotal(before, old, value, target, editing);
        value = value.withMonthlyBudgetId(target.id);
        save(value, editing);
        try { requireBalance(value.month, before, after); }
        catch (FinanceFailure error) {
            state.budgets.remove(value);
            if (old != null) state.budgets.add(old);
            throw error;
        }
    }

    /** Mock of DELETE /monthly-budgets/{id}: idempotent, not for the current month, archives children too. */
    public void deleteMonthlyBudget(String id, long nowMillis) {
        MonthlyBudget monthly = monthlyBudget(id);
        if (monthly == null) throw new FinanceFailure(FinanceProblem.NOT_FOUND);
        if (monthly.archivedAt != null) return;
        BudgetPolicy.validateDeletion(monthly,monthAt(nowMillis));
        String at = now();
        for (int i=0;i<state.budgets.size();i++) {
            Budget item=state.budgets.get(i);
            if (id.equals(item.monthlyBudgetId) && item.archivedAt == null) state.budgets.set(i,item.withArchivedAt(at));
        }
        state.monthlyBudgets.set(state.monthlyBudgets.indexOf(monthly),monthly.withArchivedAt(at));
    }

    /** Balance block: only when the total increases; still-to-spend must be covered by spendable wallets. */
    private void requireBalance(String month, long before, long after) {
        BudgetPolicy.requireBalance(before, after, () -> total(TransactionType.EXPENSE, month), this::spendable);
    }

    private static FinanceFailure conflict(String code) {
        return new FinanceFailure(FinanceProblem.CONFLICT,code,Collections.emptyList(),null);
    }

    // Tìm kiếm và lọc danh sách giao dịch theo tháng, loại, từ khóa, ví hoặc danh mục
    public List<Transaction> transactions(String month, TransactionType type, String query, String walletId, String categoryId) { return TransactionQueryEngine.transactions(this, state, month, type, query, walletId, categoryId); }

    /** Server-side history filter (contract section 5). Dates are inclusive YYYY-MM-DD in the user's timezone. */
    public static final class Query {
        public final String dateFrom, dateTo, walletId, categoryId, q, sort;
        public final TransactionType type;
        public Query(String dateFrom, String dateTo, TransactionType type, String walletId, String categoryId, String q, String sort) {
            this.dateFrom = dateFrom; this.dateTo = dateTo; this.type = type; this.walletId = walletId; this.categoryId = categoryId;
            String text = q == null ? "" : q.trim();
            this.q = text.isEmpty() ? null : text.length() > 200 ? text.substring(0, 200) : text;
            this.sort = sort == null ? "occurredAt,desc" : sort;
        }
        public static Query month(String month, TransactionType type, String walletId, String categoryId, String q) {
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
        public Page(List<Transaction> items, int page, int totalPages) {
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
            this.page = page; this.totalPages = totalPages;
        }
    }

    /** Mock implementation of GET /transactions: POSTED only, literal case-insensitive note/payee search, allowlisted sort. */
    public Page query(Query query, int page, int size) { return TransactionQueryEngine.query(this, state, query, page, size); }

    /** Appends a later page, skipping IDs already shown (pages may shift under concurrent writes). */
    public static List<Transaction> merge(List<Transaction> rows, List<Transaction> next) { return TransactionQueryEngine.merge(rows, next); }

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
    public static void validateMonth(String month) { BudgetPolicy.validateMonth(month); }

    private void checkContributions(Transaction old, Transaction value) {
        for (String id : TransactionPolicy.affectedWalletIds(old, value)) {
            long candidate = Math.addExact(Math.subtractExact(balance(id), TransactionPolicy.contribution(old, id)),
                    TransactionPolicy.contribution(value, id));
            if (candidate < 0) throw new FinanceFailure(FinanceProblem.INSUFFICIENT_FUNDS);
        }
    }

    private static boolean sameName(String first, String second) {
        return Normalizer.normalize(first, Normalizer.Form.NFC).equalsIgnoreCase(Normalizer.normalize(second, Normalizer.Form.NFC));
    }

    public void updateWallet(String id, String name, long opening, WalletType type, boolean included, int order) {
        Wallet old = wallet(id);
        if (old == null) throw new FinanceFailure(FinanceProblem.NOT_FOUND);
        if (old.archivedAt != null) throw new FinanceFailure(FinanceProblem.ARCHIVED);
        name = validName(name);
        if (opening < 0 || opening > MAX_AMOUNT || type == null || order < 0) throw new FinanceFailure(FinanceProblem.AMOUNT);
        for (Wallet item : state.wallets) if (!id.equals(item.id) && item.archivedAt == null && sameName(item.name, name))
            throw new FinanceFailure(FinanceProblem.NAME);
        if (Math.addExact(Math.subtractExact(balance(id), old.openingBalance), opening) < 0)
            throw new FinanceFailure(FinanceProblem.INSUFFICIENT_FUNDS);
        state.wallets.set(state.wallets.indexOf(old),new Wallet(old.id,name,opening,type,old.archivedAt,included,order,old.serverBalanceMinor));
    }

    public void walletStatus(String id, boolean active) {
        Wallet item = wallet(id);
        if (item == null) throw new FinanceFailure(FinanceProblem.NOT_FOUND);
        if (active) {
            for (Wallet other : state.wallets) if (!id.equals(other.id) && other.archivedAt == null && sameName(other.name, item.name))
                throw new FinanceFailure(FinanceProblem.NAME);
            state.wallets.set(state.wallets.indexOf(item),item.withArchivedAt(null));
        } else if (item.archivedAt == null) state.wallets.set(state.wallets.indexOf(item),item.withArchivedAt(now()));
    }

    public void updateCategory(String id, String name, TransactionType type, String icon, String color, int order) {
        Category old = category(id);
        if (old == null) throw new FinanceFailure(FinanceProblem.NOT_FOUND);
        if (old.archivedAt != null) throw new FinanceFailure(FinanceProblem.ARCHIVED);
        name = validName(name);
        if (type == null || type == TransactionType.TRANSFER || order < 0) throw new FinanceFailure(FinanceProblem.CATEGORY);
        if (old.type != type) {
            for (Transaction transaction : state.transactions) if (id.equals(transaction.categoryId)) throw new FinanceFailure(FinanceProblem.CONFLICT);
            for (Budget budget : state.budgets) if (id.equals(budget.categoryId)) throw new FinanceFailure(FinanceProblem.CONFLICT);
        }
        for (Category item : state.categories) if (!id.equals(item.id) && item.archivedAt == null && item.type == type && sameName(item.name, name))
            throw new FinanceFailure(FinanceProblem.NAME);
        state.categories.set(state.categories.indexOf(old),new Category(old.id,name,type,old.archivedAt,icon,color,order));
    }

    public void categoryStatus(String id, boolean active) {
        Category item = category(id);
        if (item == null) throw new FinanceFailure(FinanceProblem.NOT_FOUND);
        if (active) {
            for (Category other : state.categories) if (!id.equals(other.id) && other.archivedAt == null && other.type == item.type && sameName(other.name, item.name))
                throw new FinanceFailure(FinanceProblem.NAME);
            state.categories.set(state.categories.indexOf(item),item.withArchivedAt(null));
        } else if (item.archivedAt == null) state.categories.set(state.categories.indexOf(item),item.withArchivedAt(now()));
    }

    // Chuẩn hóa chuỗi văn bản (bỏ dấu tiếng Việt, chuyển chữ thường) để hỗ trợ tìm kiếm không dấu
}
