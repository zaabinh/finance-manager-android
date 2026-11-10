package com.fintrack.api.service.budget;

import com.fintrack.api.dto.request.budget.MonthlyBudgetRequest;
import com.fintrack.api.dto.response.budget.BudgetResponse;
import com.fintrack.api.dto.response.budget.MonthlyBudgetResponse;
import com.fintrack.api.exception.BudgetShortfallException;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.repository.BudgetRepository;
import com.fintrack.api.repository.MonthlyBudgetRepository;
import com.fintrack.api.service.finance.FinanceSupport;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monthly budget = container for one month; total = SUM(active category limits), derived on read.
 * Every write under a monthly budget locks its row first (monthly budget, category budget, category).
 */
@Service
@Profile("!test")
public class MonthlyBudgetService {
    private final MonthlyBudgetRepository monthlies;
    private final BudgetRepository budgets;
    private final FinanceSupport finance;
    private final Clock clock;

    @Autowired
    public MonthlyBudgetService(MonthlyBudgetRepository monthlies, BudgetRepository budgets, FinanceSupport finance) {
        this(monthlies, budgets, finance, Clock.systemUTC());
    }
    public MonthlyBudgetService(MonthlyBudgetRepository monthlies, BudgetRepository budgets, FinanceSupport finance, Clock clock) {
        this.monthlies = monthlies; this.budgets = budgets; this.finance = finance; this.clock = clock;
    }

    // REPEATABLE READ: parent aggregates and children come from one snapshot.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<MonthlyBudgetResponse> list(UUID user, String month, String status) {
        YearMonth parsed = month == null ? null : BudgetService.queryMonth(month);
        return responses(user, monthlies.list(user, parsed, FinanceSupport.lifecycle(status == null ? "ACTIVE" : status)));
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MonthlyBudgetResponse detail(UUID user, UUID id) { return responses(user, List.of(monthlies.detail(user, id))).get(0); }

    @Transactional
    public MonthlyBudgetResponse create(UUID user, MonthlyBudgetRequest request) {
        if (request == null) throw FinanceSupport.invalid("month is required");
        YearMonth month = BudgetService.month(request.month);
        if (request.categories == null || request.categories.isEmpty())
            throw FinanceSupport.invalid("At least one category budget is required");
        Set<UUID> seen = new HashSet<>();
        long total = 0;
        for (MonthlyBudgetRequest.CategoryLimit item : request.categories) {
            if (item == null || item.categoryId == null) throw FinanceSupport.invalid("categoryId is required");
            if (!seen.add(item.categoryId)) throw FinanceSupport.invalid("Each category can appear only once");
            total = Math.addExact(total, FinanceSupport.money(item.limitMinor, false));
        }
        if (monthlies.lockActive(user, month).isPresent()) throw duplicateMonth();
        UUID id;
        try { id = monthlies.create(user, month); }
        catch (DataIntegrityViolationException error) { throw BudgetService.unique(error, duplicateMonth()); }
        // Categories are locked in the shared LOCK_ORDER after the (new) monthly row.
        List<UUID> ordered = new ArrayList<>(seen);
        ordered.sort(FinanceSupport.LOCK_ORDER);
        for (UUID category : ordered) BudgetService.requireExpenseCategory(finance, user, category, false);
        requireBalance(monthlies, user, month, 0, total);
        try {
            for (MonthlyBudgetRequest.CategoryLimit item : request.categories)
                budgets.create(user, id, item.categoryId, month, item.limitMinor);
        } catch (DataIntegrityViolationException error) { throw BudgetService.duplicate(error); }
        return detail(user, id);
    }

    /** Idempotent archive; the current month (user timezone) cannot be deleted. Children are archived together. */
    @Transactional
    public void delete(UUID user, UUID id) {
        var row = monthlies.lock(user, id);
        if (!row.active()) return;
        if (row.month().equals(YearMonth.now(clock.withZone(finance.zone(user)))))
            throw FinanceSupport.conflict(ErrorCode.BUDGET_MONTH_CURRENT, "Không thể xóa ngân sách của tháng hiện tại");
        monthlies.archive(user, id);
    }

    /**
     * Balance block, only when the monthly total increases: still-to-spend after the change must be covered by
     * spendable wallets (active, included in assets, not SAVINGS). Point-in-time; decreases are never blocked.
     */
    static void requireBalance(MonthlyBudgetRepository monthlies, UUID user, YearMonth month, long before, long after) {
        if (after <= before) return;
        long stillToSpend = Math.max(0, Math.subtractExact(after, monthlies.spent(user, month)));
        long available = monthlies.available(user);
        if (stillToSpend > available) throw new BudgetShortfallException(Math.subtractExact(stillToSpend, available));
    }

    private List<MonthlyBudgetResponse> responses(UUID user, List<MonthlyBudgetRepository.Summary> rows) {
        Map<UUID, List<BudgetResponse>> children = new LinkedHashMap<>();
        for (var row : rows) children.put(row.id(), new ArrayList<>());
        for (BudgetResponse child : budgets.byMonthly(user, children.keySet()))
            children.get(child.monthlyBudgetId()).add(child);
        List<MonthlyBudgetResponse> result = new ArrayList<>();
        for (var row : rows) result.add(MonthlyBudgetResponse.of(row.id(), row.month().toString(), row.totalMinor(),
                row.spentMinor(), row.threshold(), row.archivedAt(), row.createdAt(), row.updatedAt(), children.get(row.id())));
        return result;
    }
    private static RuntimeException duplicateMonth() {
        return FinanceSupport.conflict(ErrorCode.BUDGET_PERIOD_CONFLICT, "Tháng này đã có ngân sách");
    }
}
