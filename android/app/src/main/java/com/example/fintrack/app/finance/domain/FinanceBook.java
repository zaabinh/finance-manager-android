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

/** Integer VND ledger. Balances and budget usage are derived, never separately persisted. */
public final class FinanceBook {
    public static final long MAX_AMOUNT = 999_999_999_999L;
    public static final TimeZone ZONE = TimeZone.getTimeZone("Asia/Ho_Chi_Minh");
    public enum Type { EXPENSE, INCOME, TRANSFER }
    public enum WalletType { CASH, BANK, SAVINGS }
    public enum Problem { AMOUNT, CATEGORY, WALLET, SAME_WALLET, DUPLICATE_BUDGET, NOT_FOUND, NAME, DATE, STORAGE,
        NETWORK, UNAUTHORIZED, CONFLICT, VALIDATION, SERVER }
    public static final class Failure extends RuntimeException {
        public final Problem problem;
        public Failure(Problem problem) { super(problem.name()); this.problem = problem; }
    }
    public static final class Wallet {
        public String id, name;
        public long openingBalance;
        public WalletType walletType = WalletType.CASH;
        public Wallet(String id, String name, long openingBalance) {
            this.id = id; this.name = name; this.openingBalance = openingBalance;
        }
    }
    public static final class Category {
        public String id, name;
        public Type type;
        public Category(String id, String name, Type type) { this.id = id; this.name = name; this.type = type; }
    }
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
    public static final class Budget {
        public String id, categoryId, month;
        public long limit;
        public Budget(String id, String categoryId, String month, long limit) {
            this.id = id; this.categoryId = categoryId; this.month = month; this.limit = limit;
        }
    }
    /** Persistence DTO, versioned so incompatible/corrupt files are never silently overwritten. */
    public static final class State {
        public int version = 1;
        public List<Wallet> wallets = new ArrayList<>();
        public List<Category> categories = new ArrayList<>();
        public List<Transaction> transactions = new ArrayList<>();
        public List<Budget> budgets = new ArrayList<>();
    }
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
    public FinanceBook(State state) {
        if (state == null || state.version != 1 || state.wallets == null || state.categories == null
                || state.transactions == null || state.budgets == null) throw new Failure(Problem.STORAGE);
        this.state = new State();
        java.util.Set<String> walletIds = new java.util.HashSet<>(), categoryIds = new java.util.HashSet<>();
        for (Wallet wallet : state.wallets) {
            if (wallet == null || wallet.id == null || wallet.id.isEmpty() || !walletIds.add(wallet.id)
                    || wallet.walletType == null || wallet.openingBalance < 0 || wallet.openingBalance > MAX_AMOUNT)
                throw new Failure(Problem.STORAGE);
            validName(wallet.name); this.state.wallets.add(wallet);
        }
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
    public void validateTotals() {
        long magnitude = 0;
        for (Wallet wallet : state.wallets) magnitude = Math.addExact(magnitude, wallet.openingBalance);
        for (Transaction transaction : state.transactions) magnitude = Math.addExact(magnitude, transaction.amount);
        long limits = 0;
        for (Budget budget : state.budgets) limits = Math.addExact(limits, budget.limit);
    }
    public Wallet wallet(String id) {
        for (Wallet item : state.wallets) if (item.id.equals(id)) return item;
        return null;
    }
    public Category category(String id) {
        for (Category item : state.categories) if (item.id.equals(id)) return item;
        return null;
    }
    public Transaction transaction(String id) {
        for (Transaction item : state.transactions) if (item.id.equals(id)) return item;
        return null;
    }
    public Budget budget(String id) {
        for (Budget item : state.budgets) if (item.id.equals(id)) return item;
        return null;
    }
    public void save(Transaction value, boolean editing) {
        if (value == null || value.id == null || value.id.isEmpty() || value.type == null) throw new Failure(Problem.NOT_FOUND);
        amount(value.amount);
        if (value.occurredAt < 0 || value.occurredAt > 253402214400000L) throw new Failure(Problem.DATE);
        if (wallet(value.walletId) == null) throw new Failure(Problem.WALLET);
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
    public void save(Budget value, boolean editing) {
        if (value == null || value.id == null || value.id.isEmpty()) throw new Failure(Problem.NOT_FOUND);
        amount(value.limit);
        validateMonth(value.month);
        Category category = category(value.categoryId);
        if (category == null || category.type != Type.EXPENSE) throw new Failure(Problem.CATEGORY);
        Budget old = budget(value.id);
        if ((editing && old == null) || (!editing && old != null)) throw new Failure(Problem.NOT_FOUND);
        for (Budget item : state.budgets) {
            if (!item.id.equals(value.id) && item.categoryId.equals(value.categoryId) && item.month.equals(value.month))
                throw new Failure(Problem.DUPLICATE_BUDGET);
        }
        if (old != null) state.budgets.remove(old);
        state.budgets.add(value);
    }
    public void deleteTransaction(String id) {
        Transaction old = transaction(id);
        if (old == null) throw new Failure(Problem.NOT_FOUND);
        state.transactions.remove(old);
    }
    public void deleteBudget(String id) {
        Budget old = budget(id);
        if (old == null) throw new Failure(Problem.NOT_FOUND);
        state.budgets.remove(old);
    }
    public Wallet addWallet(String name, long openingBalance) {
        return addWallet(name, openingBalance, WalletType.CASH);
    }
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
    public Category addCategory(String name, Type type) {
        name = validName(name);
        if (type == null || type == Type.TRANSFER) throw new Failure(Problem.CATEGORY);
        for (Category item : state.categories) if (item.type == type && fold(item.name).equals(fold(name))) throw new Failure(Problem.NAME);
        Category category = new Category(UUID.randomUUID().toString(), name, type);
        state.categories.add(category);
        return category;
    }
    private static String validName(String name) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 100) throw new Failure(Problem.NAME);
        return name.trim();
    }
    public static long parseAmount(String text) {
        if (text == null || !text.matches("[0-9]{1,12}|[0-9]{1,3}(\\.[0-9]{3}){1,3}")) throw new Failure(Problem.AMOUNT);
        try { long value = Long.parseLong(text.replace(".", "")); amount(value); return value; }
        catch (NumberFormatException e) { throw new Failure(Problem.AMOUNT); }
    }
    private static void amount(long value) { if (value <= 0 || value > MAX_AMOUNT) throw new Failure(Problem.AMOUNT); }
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
    public long total(Type type, String month) {
        long total = 0;
        for (Transaction item : transactions(month, type, "", null, null)) total = Math.addExact(total, item.amount);
        return total;
    }
    public long assets(boolean savingsOnly) {
        long result = 0;
        for (Wallet wallet : state.wallets) if (!savingsOnly || wallet.walletType == WalletType.SAVINGS)
            result = Math.addExact(result, balance(wallet.id));
        return result;
    }
    public long spent(Budget budget) {
        long total = 0;
        for (Transaction item : transactions(budget.month, Type.EXPENSE, "", null, budget.categoryId)) total = Math.addExact(total, item.amount);
        return total;
    }
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
    public BudgetUsage usage(Budget budget) { return new BudgetUsage(spent(budget), budget.limit); }
    public long budgetLimit(String month) {
        long result = 0; for (Budget item : budgets(month)) result = Math.addExact(result, item.limit); return result;
    }
    public long budgetSpent(String month) {
        long result = 0; for (Budget item : budgets(month)) result = Math.addExact(result, spent(item)); return result;
    }
    public List<Budget> budgets(String month) {
        validateMonth(month);
        List<Budget> result = new ArrayList<>();
        for (Budget item : state.budgets) if (item.month.equals(month)) result.add(item);
        return Collections.unmodifiableList(result);
    }
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
            String haystack = item.note + " " + item.payee + " " + (category == null ? "" : category.name)
                    + " " + (wallet == null ? "" : wallet.name) + " " + (destination == null ? "" : destination.name)
                    + " " + item.amount;
            if (fold(haystack).contains(search)) result.add(item);
        }
        result.sort(Comparator.comparingLong((Transaction item) -> item.occurredAt).reversed().thenComparing(item -> item.id));
        return Collections.unmodifiableList(result);
    }
    public static String month(long timestamp) {
        Calendar c = Calendar.getInstance(ZONE); c.setTimeInMillis(timestamp);
        return String.format(Locale.ROOT, "%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
    }
    public static void validateMonth(String month) {
        if (month == null || !month.matches("[1-9][0-9]{3}-(0[1-9]|1[0-2])")) throw new Failure(Problem.DATE);
    }
    private static String fold(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
