package com.fintrack.api.service.budget;

import com.fintrack.api.dto.request.budget.BudgetRequest;
import com.fintrack.api.dto.response.budget.BudgetResponse;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.repository.BudgetRepository;
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

@Service
@Profile("!test")
public class BudgetService {
    private final BudgetRepository budgets;
    private final FinanceSupport finance;
    public BudgetService(BudgetRepository budgets, FinanceSupport finance) { this.budgets = budgets; this.finance = finance; }

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
        category(user, request.categoryId, false);
        try {
            UUID id = budgets.create(user, request.categoryId, month, request.limitMinor);
            return budgets.detail(user, id);
        } catch (DataIntegrityViolationException error) { throw duplicate(error); }
    }
    @Transactional
    public BudgetResponse update(UUID user, UUID id, BudgetRequest request) {
        YearMonth month = validate(request);
        var old = budgets.lock(user, id);
        if (!old.active()) throw FinanceSupport.conflict(ErrorCode.BUDGET_ARCHIVED, "Archived budget cannot be edited");
        category(user, request.categoryId, old.categoryId().equals(request.categoryId));
        try {
            budgets.update(user, id, request.categoryId, month, request.limitMinor);
            return budgets.detail(user, id);
        } catch (DataIntegrityViolationException error) { throw duplicate(error); }
    }
    @Transactional
    public void delete(UUID user, UUID id) {
        if (budgets.lock(user, id).active()) budgets.archive(user, id);
    }
    private void category(UUID user, UUID id, boolean retained) {
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
    private static YearMonth queryMonth(String value) {
        try { return month(value); }
        catch (com.fintrack.api.exception.AppException error) { throw FinanceSupport.badRequest(error.getMessage()); }
    }
    private RuntimeException duplicate(DataIntegrityViolationException error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23P01".equals(sql.getSQLState()))
                return FinanceSupport.conflict(ErrorCode.BUDGET_PERIOD_CONFLICT, "A budget already exists for this category and month");
        }
        return error;
    }
}
