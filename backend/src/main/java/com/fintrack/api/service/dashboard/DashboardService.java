package com.fintrack.api.service.dashboard;

import com.fintrack.api.dto.response.budget.BudgetResponse;
import com.fintrack.api.dto.response.dashboard.DashboardResponse;
import com.fintrack.api.repository.BudgetRepository;
import com.fintrack.api.repository.DashboardRepository;
import com.fintrack.api.service.budget.BudgetService;
import com.fintrack.api.service.finance.FinanceSupport;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!test")
public class DashboardService {
    private final DashboardRepository dashboard;
    private final BudgetRepository budgets;
    public DashboardService(DashboardRepository dashboard, BudgetRepository budgets) {
        this.dashboard = dashboard; this.budgets = budgets;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DashboardResponse get(UUID user, String optionalMonth) {
        YearMonth month = optionalMonth == null ? null : queryMonth(optionalMonth);
        var profile = dashboard.profile(user);
        if (month == null) month = YearMonth.from(profile.snapshotAt().atZone(profile.zone()));
        var start = month.atDay(1).atStartOfDay(profile.zone()).toInstant();
        var end = month.plusMonths(1).atDay(1).atStartOfDay(profile.zone()).toInstant();
        return new DashboardResponse(month.toString(), profile.zone().getId(), "VND", profile.snapshotAt(),
                new DashboardResponse.User(profile.displayName()), dashboard.assets(user), dashboard.flow(user, start, end),
                summarize(budgets.dashboardActive(user, month)), dashboard.recent(user, start, end),
                new DashboardResponse.Notifications(false, null));
    }

    public static YearMonth queryMonth(String value) {
        try { return BudgetService.month(value); }
        catch (com.fintrack.api.exception.AppException error) { throw FinanceSupport.badRequest(error.getMessage()); }
    }

    static DashboardResponse.Budget summarize(List<BudgetResponse> rows) {
        BigDecimal limits = BigDecimal.ZERO, spent = BigDecimal.ZERO;
        int warnings = 0, exceeded = 0;
        for (var budget : rows) {
            limits = limits.add(BigDecimal.valueOf(budget.limitMinor()));
            spent = spent.add(BigDecimal.valueOf(budget.spentMinor()));
            if ("EXCEEDED".equals(budget.status())) exceeded++;
            else if ("WARNING".equals(budget.status())) warnings++;
        }
        long totalLimit = FinanceSupport.exact(limits), totalSpent = FinanceSupport.exact(spent);
        var percent = BudgetResponse.percent(totalLimit, totalSpent);
        int progress = percent.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100)).intValue();
        String worst = rows.isEmpty() ? "NONE" : exceeded > 0 ? "EXCEEDED" : warnings > 0 ? "WARNING" : "SAFE";
        return new DashboardResponse.Budget(rows.size(), totalLimit, totalSpent, FinanceSupport.exact(limits.subtract(spent)),
                percent, progress, worst, warnings, exceeded);
    }
}
