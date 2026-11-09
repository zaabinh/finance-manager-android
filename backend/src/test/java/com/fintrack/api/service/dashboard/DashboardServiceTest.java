package com.fintrack.api.service.dashboard;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fintrack.api.dto.response.budget.BudgetResponse;
import com.fintrack.api.dto.response.dashboard.DashboardResponse.*;
import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.repository.BudgetRepository;
import com.fintrack.api.repository.DashboardRepository;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {
    @Test void worstIndividualStatusCannotBeHiddenByAggregateProgress() {
        var summary = DashboardService.summarize(List.of(budget(100, 100, 80), budget(900, 0, 80)));
        assertThat(summary.worstStatus()).isEqualTo("EXCEEDED");
        assertThat(summary.usagePercent()).isEqualByComparingTo("10.00");
        assertThat(summary.exceededCount()).isEqualTo(1);
        assertThat(summary.progressPercent()).isEqualTo(10);
    }
    @Test void warningsUseExactUnroundedThresholdAndProgressOnlyIsCapped() {
        assertThat(DashboardService.summarize(List.of(budget(100_000, 79_999, 80))).worstStatus()).isEqualTo("SAFE");
        var warning = DashboardService.summarize(List.of(budget(100_000, 80_000, 80)));
        assertThat(warning.worstStatus()).isEqualTo("WARNING");
        assertThat(warning.warningCount()).isEqualTo(1);
        var exceeded = DashboardService.summarize(List.of(budget(100, 250, 100)));
        assertThat(exceeded.usagePercent()).isEqualByComparingTo("250.00");
        assertThat(exceeded.progressPercent()).isEqualTo(100);
        assertThat(exceeded.remainingMinor()).isEqualTo(-150);
    }
    @Test void noCategoryBudgetMeansNoneIncludingAnEmptyMonthlyParent() {
        var summary = DashboardService.summarize(List.of());
        assertThat(summary.worstStatus()).isEqualTo("NONE");
        assertThat(summary.limitMinor()).isZero();
        assertThat(summary.usagePercent()).isEqualByComparingTo("0.00");
    }
    @Test void budgetTotalsOverflowInsteadOfWrapping() {
        assertThatThrownBy(() -> DashboardService.summarize(List.of(budget(Long.MAX_VALUE, 0, 80), budget(1, 0, 80))))
                .isInstanceOfSatisfying(AppException.class, error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.FINANCE_AMOUNT_OVERFLOW));
        assertThatThrownBy(() -> DashboardService.summarize(List.of(budget(1, Long.MAX_VALUE, 80), budget(1, 1, 80))))
                .isInstanceOf(AppException.class);
    }
    @Test void strictMonthAcceptsBoundariesAndRejectsInvalidDates() {
        for (String value : List.of("0001-01", "2024-02", "9998-12"))
            assertThat(DashboardService.queryMonth(value)).isEqualTo(YearMonth.parse(value));
        for (String value : List.of("", "2026-1", "2026-13", "0000-01", "9999-12", " 2026-11", "2026-11-01"))
            assertThatThrownBy(() -> DashboardService.queryMonth(value)).isInstanceOfSatisfying(AppException.class,
                    error -> assertThat(error.getStatus().value()).isEqualTo(400));
    }
    @Test void defaultMonthUsesSnapshotTimestampAndUserTimezone() {
        var repository = mock(DashboardRepository.class); var budgets = mock(BudgetRepository.class);
        UUID user = UUID.randomUUID();
        when(repository.profile(user)).thenReturn(new DashboardRepository.Profile("An", ZoneId.of("America/New_York"), Instant.parse("2026-12-01T01:00:00Z")));
        when(repository.assets(user)).thenReturn(new Assets(0, 0, 0, 0));
        when(repository.flow(any(), any(), any())).thenReturn(new MonthlyFlow(0, 0, 0));
        when(budgets.dashboardActive(user, YearMonth.of(2026, 11))).thenReturn(List.of());
        var response = new DashboardService(repository, budgets).get(user, null);
        assertThat(response.month()).isEqualTo("2026-11");
        assertThat(response.notifications().available()).isFalse();
        assertThat(response.notifications().unreadCount()).isNull();
        verify(repository).flow(user, Instant.parse("2026-11-01T04:00:00Z"), Instant.parse("2026-12-01T05:00:00Z"));
    }
    private BudgetResponse budget(long limit, long spent, int threshold) {
        return BudgetResponse.of(UUID.randomUUID(), UUID.randomUUID(), "2026-11", limit, spent, threshold, null, Instant.EPOCH, Instant.EPOCH);
    }
}
