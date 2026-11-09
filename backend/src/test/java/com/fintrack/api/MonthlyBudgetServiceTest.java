package com.fintrack.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.api.dto.request.budget.MonthlyBudgetRequest;
import com.fintrack.api.dto.response.budget.BudgetResponse;
import com.fintrack.api.dto.response.budget.MonthlyBudgetResponse;
import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.BudgetShortfallException;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.repository.BudgetRepository;
import com.fintrack.api.repository.MonthlyBudgetRepository;
import com.fintrack.api.service.budget.MonthlyBudgetService;
import com.fintrack.api.service.finance.FinanceSupport;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class MonthlyBudgetServiceTest {
    private final MonthlyBudgetRepository monthlies = mock(MonthlyBudgetRepository.class);
    private final BudgetRepository budgets = mock(BudgetRepository.class);
    private final FinanceSupport finance = mock(FinanceSupport.class);
    // 2026-11-30T18:00Z is already 2026-12-01 01:00 in Asia/Ho_Chi_Minh.
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-30T18:00:00Z"), ZoneOffset.UTC);
    private final MonthlyBudgetService service = new MonthlyBudgetService(monthlies, budgets, finance, clock);
    private final UUID user = UUID.randomUUID(), id = UUID.randomUUID(), food = UUID.randomUUID(), rent = UUID.randomUUID();
    private static final YearMonth NOV = YearMonth.of(2026, 11), DEC = YearMonth.of(2026, 12);

    @BeforeEach void defaults() {
        when(finance.zone(user)).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        when(finance.category(eq(user), any(), eq(true))).thenAnswer(call -> new FinanceSupport.CategoryRef(call.getArgument(1), "EXPENSE", true));
        when(monthlies.lockActive(any(), any())).thenReturn(Optional.empty());
        when(monthlies.create(user, NOV)).thenReturn(id);
        when(monthlies.available(user)).thenReturn(10_000_000L);
        when(monthlies.detail(user, id)).thenReturn(summary(150_000, 0, null));
    }

    @Test void createWritesParentAndAllCategoriesAtomicallyWithDerivedTotal() {
        when(budgets.byMonthly(eq(user), any())).thenReturn(List.of(child(food, 100_000), child(rent, 50_000)));
        var response = service.create(user, request("2026-11", line(food, 100_000L), line(rent, 50_000L)));
        verify(monthlies).create(user, NOV);
        verify(budgets).create(user, id, food, NOV, 100_000);
        verify(budgets).create(user, id, rent, NOV, 50_000);
        assertThat(response.totalLimitMinor()).isEqualTo(150_000);
        assertThat(response.categories()).hasSize(2);
        assertThat(response.status()).isEqualTo("SAFE");
    }
    @Test void createRequiresAtLeastOneDistinctValidCategory() {
        assertError(() -> service.create(user, request("2026-11")), ErrorCode.VALIDATION_ERROR);
        assertError(() -> service.create(user, request("2026-11", line(food, 1L), line(food, 2L))), ErrorCode.VALIDATION_ERROR);
        assertError(() -> service.create(user, request("2026-11", line(food, 0L))), ErrorCode.VALIDATION_ERROR);
        assertError(() -> service.create(user, request("2026-13", line(food, 1L))), ErrorCode.VALIDATION_ERROR);
        when(finance.category(user, rent, true)).thenReturn(new FinanceSupport.CategoryRef(rent, "INCOME", true));
        assertError(() -> service.create(user, request("2026-11", line(rent, 1L))), ErrorCode.VALIDATION_ERROR);
        verify(budgets, never()).create(any(), any(), any(), any(), anyLong());
    }
    @Test void createRejectsSecondActiveMonthlyBudgetForTheMonth() {
        when(monthlies.lockActive(user, NOV)).thenReturn(Optional.of(new MonthlyBudgetRepository.MonthlyRow(UUID.randomUUID(), NOV, true)));
        assertError(() -> service.create(user, request("2026-11", line(food, 1L))), ErrorCode.BUDGET_PERIOD_CONFLICT);
        when(monthlies.lockActive(user, NOV)).thenReturn(Optional.empty());
        when(monthlies.create(user, NOV)).thenThrow(new DataIntegrityViolationException("race", new SQLException("dup", "23505")));
        assertError(() -> service.create(user, request("2026-11", line(food, 1L))), ErrorCode.BUDGET_PERIOD_CONFLICT);
    }
    @Test void createIsBlockedWhenStillToSpendExceedsSpendableBalance() {
        when(monthlies.spent(user, NOV)).thenReturn(30_000L);
        when(monthlies.available(user)).thenReturn(100_000L);
        // total 150 000 - spent 30 000 = 120 000 still to spend; shortfall 20 000; nothing written for categories.
        assertThatThrownBy(() -> service.create(user, request("2026-11", line(food, 100_000L), line(rent, 50_000L))))
                .isInstanceOfSatisfying(BudgetShortfallException.class, error -> {
                    assertThat(error.getStatus().value()).isEqualTo(409);
                    assertThat(error.getShortfallMinor()).isEqualTo(20_000);
                });
        verify(budgets, never()).create(any(), any(), any(), any(), anyLong());
    }
    @Test void deleteIsRejectedForCurrentMonthInUserTimezone() {
        when(monthlies.lock(user, id)).thenReturn(new MonthlyBudgetRepository.MonthlyRow(id, DEC, true));
        assertError(() -> service.delete(user, id), ErrorCode.BUDGET_MONTH_CURRENT);
        verify(monthlies, never()).archive(any(), any());
    }
    @Test void deletePastOrFutureMonthArchivesWithChildrenAndIsIdempotent() {
        when(monthlies.lock(user, id)).thenReturn(new MonthlyBudgetRepository.MonthlyRow(id, NOV, true),
                new MonthlyBudgetRepository.MonthlyRow(id, NOV, false));
        service.delete(user, id); service.delete(user, id);
        verify(monthlies, times(1)).archive(user, id);
        UUID future = UUID.randomUUID();
        when(monthlies.lock(user, future)).thenReturn(new MonthlyBudgetRepository.MonthlyRow(future, YearMonth.of(2027, 1), true));
        service.delete(user, future);
        verify(monthlies).archive(user, future);
    }
    @Test void unownedMonthlyBudgetIs404() {
        UUID other = UUID.randomUUID();
        when(monthlies.lock(user, other)).thenThrow(FinanceSupport.notFound());
        when(monthlies.detail(user, other)).thenThrow(FinanceSupport.notFound());
        assertError(() -> service.delete(user, other), ErrorCode.NOT_FOUND);
        assertError(() -> service.detail(user, other), ErrorCode.NOT_FOUND);
    }
    @Test void listMapsChildrenToTheirParentAndDefaultsToActive() {
        UUID second = UUID.randomUUID();
        when(monthlies.list(user, null, "ACTIVE")).thenReturn(List.of(summary(100_000, 120_000, null),
                new MonthlyBudgetRepository.Summary(second, YearMonth.of(2026, 10), 0, 0, 80, null, Instant.EPOCH, Instant.EPOCH)));
        when(budgets.byMonthly(eq(user), any())).thenReturn(List.of(child(food, 100_000)));
        var result = service.list(user, null, null);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).categories()).hasSize(1);
        assertThat(result.get(0).remainingMinor()).isEqualTo(-20_000);
        assertThat(result.get(0).status()).isEqualTo("EXCEEDED");
        assertThat(result.get(1).categories()).isEmpty();
        assertThat(result.get(1).totalLimitMinor()).isZero();
        assertThat(result.get(1).status()).isEqualTo("SAFE");
        assertThatThrownBy(() -> service.list(user, "2026-1", null)).isInstanceOfSatisfying(AppException.class,
                error -> assertThat(error.getStatus().value()).isEqualTo(400));
    }
    @Test void totalIsDerivedAndUsageMatchesCategoryRules() {
        var response = MonthlyBudgetResponse.of(id, "2026-11", 100_000, 80_000, 80, null, Instant.EPOCH, Instant.EPOCH, List.of());
        assertThat(response.status()).isEqualTo("WARNING");
        assertThat(response.usagePercent()).isEqualByComparingTo("80.00");
        assertThat(response.remainingMinor()).isEqualTo(20_000);
    }
    @Test void jsonRejectsTotalFieldUnknownNestedFieldsAndFractions() {
        ObjectMapper mapper = new ObjectMapper();
        assertThatThrownBy(() -> mapper.readValue("{\"month\":\"2026-11\",\"totalLimitMinor\":5,\"categories\":[]}", MonthlyBudgetRequest.class))
                .hasRootCauseInstanceOf(AppException.class);
        assertThatThrownBy(() -> mapper.readValue("{\"month\":\"2026-11\",\"categories\":[{\"categoryId\":\"" + food + "\",\"limitMinor\":1,\"spentMinor\":0}]}", MonthlyBudgetRequest.class))
                .hasRootCauseInstanceOf(AppException.class);
        assertThatThrownBy(() -> mapper.readValue("{\"month\":\"2026-11\",\"categories\":[{\"categoryId\":\"" + food + "\",\"limitMinor\":1.5}]}", MonthlyBudgetRequest.class))
                .hasRootCauseInstanceOf(AppException.class);
    }

    private MonthlyBudgetRepository.Summary summary(long total, long spent, Instant archivedAt) {
        return new MonthlyBudgetRepository.Summary(id, NOV, total, spent, 80, archivedAt, Instant.EPOCH, Instant.EPOCH);
    }
    private BudgetResponse child(UUID category, long limit) {
        return BudgetResponse.of(UUID.randomUUID(), id, category, "2026-11", limit, 0, 80, null, Instant.EPOCH, Instant.EPOCH);
    }
    private static MonthlyBudgetRequest.CategoryLimit line(UUID category, Long limit) {
        var line = new MonthlyBudgetRequest.CategoryLimit(); line.categoryId = category; line.limitMinor = limit; return line;
    }
    private static MonthlyBudgetRequest request(String month, MonthlyBudgetRequest.CategoryLimit... lines) {
        var request = new MonthlyBudgetRequest(); request.month = month; request.categories = List.of(lines); return request;
    }
    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(AppException.class, error -> assertThat(error.getErrorCode()).isEqualTo(code));
    }
}
