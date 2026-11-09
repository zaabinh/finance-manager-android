package com.fintrack.api.service.budget;

import com.fintrack.api.dto.request.budget.BudgetRequest;
import com.fintrack.api.dto.response.budget.BudgetResponse;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.repository.BudgetRepository;
import com.fintrack.api.repository.MonthlyBudgetRepository;
import com.fintrack.api.service.finance.FinanceSupport;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Category budgets. Each lives under the active monthly budget of its month; writes lock that parent first. */
@Service
@Profile("!test")
public class BudgetService {
    private final BudgetRepository budgets;
    private final FinanceSupport finance;
    private final MonthlyBudgetRepository monthlies;
    public BudgetService(BudgetRepository budgets, FinanceSupport finance, MonthlyBudgetRepository monthlies) {
        this.budgets = budgets; this.finance = finance; this.monthlies = monthlies;
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> list(UUID user, String month, UUID category, String status) {
        YearMonth parsed = month == null ? null : queryMonth(month);
        String lifecycle = FinanceSupport.lifecycle(status == null ? "ACTIVE" : status);
        if (category != null) finance.category(user, category, false);
        return budgets.list(user, parsed, category, lifecycle);
    }
    @Transactional(readOnly = true)
    public BudgetResponse detail(UUID user, UUID id) { return budgets.detail(user, id); }

    @Transactional
    public BudgetResponse create(UUID user, BudgetRequest request) {
        YearMonth month = validate(request);
        var parent = monthlies.lockActive(user, month).orElseThrow(BudgetService::monthMissing);
        requireExpenseCategory(finance, user, request.categoryId, false);
        long before = monthlies.total(user, parent.id());
        try {
            UUID id = budgets.create(user, parent.id(), request.categoryId, month, request.limitMinor);
            MonthlyBudgetService.requireBalance(monthlies, user, month, before, Math.addExact(before, request.limitMinor));
            monthlies.touch(user, parent.id());
            return budgets.detail(user, id);
        } catch (DataIntegrityViolationException error) { throw duplicate(error); }
    }
    @Transactional
    public BudgetResponse update(UUID user, UUID id, BudgetRequest request) {
        YearMonth month = validate(request);
        var peek = budgets.find(user, id);
        if (!peek.active()) throw archived();
        // Lock order: monthly budgets (ascending month, deterministic), then the category budget, then the category.
        YearMonth first = peek.month().isBefore(month) ? peek.month() : month, second = first.equals(month) ? peek.month() : month;
        var lockedFirst = monthlies.lockActive(user, first);
        var lockedSecond = first.equals(second) ? lockedFirst : monthlies.lockActive(user, second);
        var target = (month.equals(first) ? lockedFirst : lockedSecond).orElseThrow(BudgetService::monthMissing);
        var old = budgets.lock(user, id);
        if (!old.active()) throw archived();
        if (!old.month().equals(peek.month()))
            throw FinanceSupport.conflict(ErrorCode.BUDGET_PERIOD_CONFLICT, "Ngân sách vừa thay đổi, hãy tải lại");
        requireExpenseCategory(finance, user, request.categoryId, old.categoryId().equals(request.categoryId));
        long before = monthlies.total(user, target.id());
        long after = old.month().equals(month) ? Math.addExact(Math.subtractExact(before, old.limitMinor()), request.limitMinor)
                : Math.addExact(before, request.limitMinor);
        try {
            budgets.update(user, id, target.id(), request.categoryId, month, request.limitMinor);
            MonthlyBudgetService.requireBalance(monthlies, user, month, before, after);
            monthlies.touch(user, target.id());
            if (old.monthlyBudgetId() != null && !old.monthlyBudgetId().equals(target.id())) monthlies.touch(user, old.monthlyBudgetId());
            return budgets.detail(user, id);
        } catch (DataIntegrityViolationException error) { throw duplicate(error); }
    }
    /** Archive (idempotent). A decrease of the monthly total is never blocked; the parent stays even at total 0. */
    @Transactional
    public void delete(UUID user, UUID id) {
        var peek = budgets.find(user, id);
        if (!peek.active()) return;
        var parent = monthlies.lockActive(user, peek.month());
        if (budgets.lock(user, id).active()) {
            budgets.archive(user, id);
            parent.ifPresent(row -> monthlies.touch(user, row.id()));
        }
    }

    static void requireExpenseCategory(FinanceSupport finance, UUID user, UUID id, boolean retained) {
        var category = finance.category(user, id, true);
        if (!"EXPENSE".equals(category.categoryType())) throw FinanceSupport.invalid("Budget requires an expense category");
        if (!retained && !category.active()) throw FinanceSupport.conflict(ErrorCode.CATEGORY_ARCHIVED, "Category is archived");
    }
    private YearMonth validate(BudgetRequest request) {
        if (request == null || request.categoryId == null) throw FinanceSupport.invalid("categoryId is required");
        FinanceSupport.money(request.limitMinor, false);
        return month(request.month);
    }
    public static YearMonth month(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}")) throw FinanceSupport.invalid("month must be YYYY-MM");
        try {
            YearMonth parsed = YearMonth.parse(value);
            if (parsed.getYear() < 1 || parsed.getYear() > 9998) throw FinanceSupport.invalid("month year must be 0001-9998");
            return parsed;
        } catch (DateTimeException error) { throw FinanceSupport.invalid("Invalid month"); }
    }
    static YearMonth queryMonth(String value) {
        try { return month(value); }
        catch (com.fintrack.api.exception.AppException error) { throw FinanceSupport.badRequest(error.getMessage()); }
    }
    private static RuntimeException monthMissing() {
        return FinanceSupport.conflict(ErrorCode.BUDGET_MONTH_MISSING, "Chưa có ngân sách tháng cho tháng này");
    }
    private static RuntimeException archived() {
        return FinanceSupport.conflict(ErrorCode.BUDGET_ARCHIVED, "Archived budget cannot be edited");
    }
    static RuntimeException duplicate(DataIntegrityViolationException error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23P01".equals(sql.getSQLState()))
                return FinanceSupport.conflict(ErrorCode.BUDGET_PERIOD_CONFLICT, "A budget already exists for this category and month");
        }
        return error;
    }
    /** Unique violation (23505) becomes the given conflict; anything else is rethrown unchanged. */
    static RuntimeException unique(DataIntegrityViolationException error, RuntimeException conflict) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) return conflict;
        }
        return error;
    }
}
