package com.fintrack.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.api.dto.request.budget.BudgetRequest;
import com.fintrack.api.dto.response.budget.BudgetResponse;
import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.BudgetShortfallException;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.repository.BudgetRepository;
import com.fintrack.api.repository.MonthlyBudgetRepository;
import com.fintrack.api.service.budget.BudgetService;
import com.fintrack.api.service.finance.FinanceSupport;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class BudgetServiceTest {
    private final BudgetRepository repository = mock(BudgetRepository.class);
    private final FinanceSupport finance = mock(FinanceSupport.class);
    private final MonthlyBudgetRepository monthlies = mock(MonthlyBudgetRepository.class);
    private final BudgetService service = new BudgetService(repository, finance, monthlies);
    private final UUID user = UUID.randomUUID(), category = UUID.randomUUID(), id = UUID.randomUUID(), parent = UUID.randomUUID();
    private static final YearMonth NOV = YearMonth.of(2026, 11);

    @BeforeEach void parentAndAmpleBalance() {
        when(monthlies.lockActive(any(), any())).thenAnswer(call -> Optional.of(new MonthlyBudgetRepository.MonthlyRow(parent, call.getArgument(1), true)));
        when(monthlies.available(any())).thenReturn(MAX);
    }
    private static final long MAX = Long.MAX_VALUE / 4;

    @Test void validatesStrictMonthIncludingCalendarEdges() {
        assertThat(BudgetService.month("2024-02").atEndOfMonth().getDayOfMonth()).isEqualTo(29);
        assertThat(BudgetService.month("9998-12").plusMonths(1)).isEqualTo(YearMonth.of(9999, 1));
        for (String value : new String[]{"2026-1", "2026-00", "2026-13", "0000-01", "9999-01", "+2026-01", "2026-01-01", "2026-01 "})
            assertThatThrownBy(() -> BudgetService.month(value)).isInstanceOf(AppException.class);
    }
    @Test void statusUsesExactThresholdBeforeRoundingAndDoesNotOverflow() {
        assertThat(usage(100_000, 79_999, 80).status()).isEqualTo("SAFE");
        assertThat(usage(100_000, 79_999, 80).usagePercent()).isEqualByComparingTo("80.00");
        assertThat(usage(100_000, 80_000, 80).status()).isEqualTo("WARNING");
        assertThat(usage(100_000, 100_000, 100).status()).isEqualTo("EXCEEDED");
        assertThat(usage(100_000, 150_000, 80).remainingMinor()).isEqualTo(-50_000);
        assertThat(usage(999_999_999_999L, Long.MAX_VALUE, 80).usagePercent()).isGreaterThan(BigDecimal.valueOf(100));
    }
    @Test void retainsArchivedExpenseCategoryWhenEditingLimitOrMonth() {
        row(true, 50);
        when(finance.category(user, category, true)).thenReturn(new FinanceSupport.CategoryRef(category, "EXPENSE", false));
        service.update(user, id, request(100L));
        verify(repository).update(user, id, parent, category, NOV, 100);
        assertThatThrownBy(() -> service.create(user, request(100L))).isInstanceOfSatisfying(AppException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CATEGORY_ARCHIVED));
    }
    @Test void rejectsIncomeCategoryInvalidLimitAndArchivedBudget() {
        when(finance.category(user, category, true)).thenReturn(new FinanceSupport.CategoryRef(category, "INCOME", true));
        assertThatThrownBy(() -> service.create(user, request(100L))).isInstanceOf(AppException.class);
        for (Long amount : new Long[]{null, 0L, -1L, 1_000_000_000_000L})
            assertThatThrownBy(() -> service.create(user, request(amount))).isInstanceOf(AppException.class);
        row(false, 50);
        assertThatThrownBy(() -> service.update(user, id, request(100L))).isInstanceOfSatisfying(AppException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.BUDGET_ARCHIVED));
        verify(repository, never()).create(any(), any(), any(), any(), anyLong());
    }
    @Test void duplicateExclusionMapsTo409WithoutMaskingOtherDatabaseErrors() {
        when(finance.category(user, category, true)).thenReturn(new FinanceSupport.CategoryRef(category, "EXPENSE", true));
        when(repository.create(any(), any(), any(), any(), anyLong())).thenThrow(new DataIntegrityViolationException("overlap", new SQLException("overlap", "23P01")));
        assertThatThrownBy(() -> service.create(user, request(100L))).isInstanceOfSatisfying(AppException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.BUDGET_PERIOD_CONFLICT));
        var other = new DataIntegrityViolationException("other", new SQLException("other", "23514"));
        doThrow(other).when(repository).create(any(), any(), any(), any(), anyLong());
        assertThatThrownBy(() -> service.create(user, request(100L))).isSameAs(other);
    }
    @Test void archiveIsIdempotentAndNeverWritesLedger() {
        var active = new BudgetRepository.BudgetRow(id, category, true, parent, NOV, 100);
        var archived = new BudgetRepository.BudgetRow(id, category, false, parent, NOV, 100);
        when(repository.find(user, id)).thenReturn(active, archived);
        when(repository.lock(user, id)).thenReturn(active);
        service.delete(user, id); service.delete(user, id);
        verify(repository, times(1)).archive(user, id);
        verifyNoInteractions(finance);
    }
    @Test void queryValidatesCategoryOwnershipAndUsesActiveByDefault() {
        service.list(user, "2026-11", category, null);
        verify(finance).category(user, category, false);
        verify(repository).list(user, YearMonth.of(2026, 11), category, "ACTIVE");
        assertThatThrownBy(() -> service.list(user, "2026-13", null, null)).isInstanceOfSatisfying(AppException.class,
                error -> assertThat(error.getStatus().value()).isEqualTo(400));
    }
    @Test void jsonRejectsFractionalStringOverflowAndReadOnlyFields() {
        ObjectMapper mapper = new ObjectMapper();
        for (String value : new String[]{"1.5", "\"100\"", "9223372036854775808", "true"}) {
            assertThatThrownBy(() -> mapper.readValue("{\"categoryId\":\"" + category + "\",\"month\":\"2026-11\",\"limitMinor\":" + value + "}", BudgetRequest.class))
                    .hasRootCauseInstanceOf(AppException.class);
        }
        assertThatThrownBy(() -> mapper.readValue("{\"spentMinor\":100}", BudgetRequest.class)).hasRootCauseInstanceOf(AppException.class);
    }
    @Test void createRequiresActiveMonthlyBudgetForTheMonth() {
        when(monthlies.lockActive(user, NOV)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(user, request(100L))).isInstanceOfSatisfying(AppException.class, error -> {
            assertThat(error.getErrorCode()).isEqualTo(ErrorCode.BUDGET_MONTH_MISSING);
            assertThat(error.getStatus().value()).isEqualTo(409);
        });
        verify(repository, never()).create(any(), any(), any(), any(), anyLong());
    }
    @Test void increaseBeyondSpendableBalanceIsBlockedWithShortfall() {
        when(finance.category(user, category, true)).thenReturn(new FinanceSupport.CategoryRef(category, "EXPENSE", true));
        when(monthlies.total(user, parent)).thenReturn(100_000L);
        when(monthlies.spent(user, NOV)).thenReturn(20_000L);
        when(monthlies.available(user)).thenReturn(100_000L);
        // after = 150 000, still to spend = 130 000, available = 100 000 -> shortfall 30 000
        assertThatThrownBy(() -> service.create(user, request(50_000L))).isInstanceOfSatisfying(BudgetShortfallException.class, error -> {
            assertThat(error.getErrorCode()).isEqualTo(ErrorCode.BUDGET_EXCEEDS_AVAILABLE_BALANCE);
            assertThat(error.getShortfallMinor()).isEqualTo(30_000);
        });
        verify(monthlies, never()).touch(any(), any());
        when(monthlies.available(user)).thenReturn(130_000L);
        when(repository.create(any(), any(), any(), any(), anyLong())).thenReturn(id);
        service.create(user, request(50_000L));
        verify(monthlies).touch(user, parent);
    }
    @Test void spendingAboveTheNewTotalMeansNothingStillToSpend() {
        when(finance.category(user, category, true)).thenReturn(new FinanceSupport.CategoryRef(category, "EXPENSE", true));
        when(monthlies.total(user, parent)).thenReturn(0L);
        when(monthlies.spent(user, NOV)).thenReturn(500_000L);
        when(monthlies.available(user)).thenReturn(0L);
        service.create(user, request(100_000L));
        verify(repository).create(user, parent, category, NOV, 100_000);
    }
    @Test void decreaseAndRemovalAreNeverBlocked() {
        row(true, 100_000);
        when(finance.category(user, category, true)).thenReturn(new FinanceSupport.CategoryRef(category, "EXPENSE", true));
        when(monthlies.total(user, parent)).thenReturn(100_000L);
        when(monthlies.available(user)).thenReturn(0L);
        service.update(user, id, request(40_000L));
        service.delete(user, id);
        verify(repository).update(user, id, parent, category, NOV, 40_000);
        verify(repository).archive(user, id);
        verify(monthlies, never()).available(any());
        verify(monthlies, never()).spent(any(), any());
    }
    @Test void sameMonthIncreaseChecksOnlyTheDelta() {
        row(true, 100_000);
        when(finance.category(user, category, true)).thenReturn(new FinanceSupport.CategoryRef(category, "EXPENSE", true));
        when(monthlies.total(user, parent)).thenReturn(100_000L);
        when(monthlies.available(user)).thenReturn(119_999L);
        assertThatThrownBy(() -> service.update(user, id, request(120_000L))).isInstanceOfSatisfying(BudgetShortfallException.class,
                error -> assertThat(error.getShortfallMinor()).isEqualTo(1));
    }
    @Test void writesLockMonthlyBudgetThenCategoryBudgetThenCategory() {
        row(true, 100);
        when(finance.category(user, category, true)).thenReturn(new FinanceSupport.CategoryRef(category, "EXPENSE", true));
        service.update(user, id, request(100L));
        var order = inOrder(monthlies, repository, finance);
        order.verify(monthlies).lockActive(user, NOV);
        order.verify(repository).lock(user, id);
        order.verify(finance).category(user, category, true);
    }
    @Test void movingToAMonthWithoutMonthlyBudgetIsRejected() {
        row(true, 100);
        when(monthlies.lockActive(user, YearMonth.of(2026, 12))).thenReturn(Optional.empty());
        BudgetRequest december = request(100L); december.month = "2026-12";
        assertThatThrownBy(() -> service.update(user, id, december)).isInstanceOfSatisfying(AppException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.BUDGET_MONTH_MISSING));
        verify(repository, never()).update(any(), any(), any(), any(), any(), anyLong());
    }
    @Test void unownedBudgetIs404() {
        when(repository.find(user, id)).thenThrow(FinanceSupport.notFound());
        assertThatThrownBy(() -> service.update(user, id, request(100L))).isInstanceOfSatisfying(AppException.class,
                error -> assertThat(error.getStatus().value()).isEqualTo(404));
        assertThatThrownBy(() -> service.delete(user, id)).isInstanceOfSatisfying(AppException.class,
                error -> assertThat(error.getStatus().value()).isEqualTo(404));
    }
    @Test void responseCarriesMonthlyBudgetIdAndZeroLimitIsSafe() {
        assertThat(BudgetResponse.of(id, parent, category, "2026-11", 100, 0, 80, null, Instant.EPOCH, Instant.EPOCH).monthlyBudgetId()).isEqualTo(parent);
        assertThat(BudgetResponse.status(0, 0, 80)).isEqualTo("SAFE");
        assertThat(BudgetResponse.status(0, 1, 80)).isEqualTo("EXCEEDED");
        assertThat(BudgetResponse.percent(0, 0)).isEqualByComparingTo("0.00");
    }
    private void row(boolean active, long limit) {
        var value = new BudgetRepository.BudgetRow(id, category, active, parent, NOV, limit);
        when(repository.find(user, id)).thenReturn(value); when(repository.lock(user, id)).thenReturn(value);
    }
    private BudgetRequest request(Long amount) {
        BudgetRequest request = new BudgetRequest(); request.categoryId = category; request.month = "2026-11"; request.limitMinor = amount; return request;
    }
    private BudgetResponse usage(long limit, long spent, int threshold) {
        return BudgetResponse.of(id, category, "2026-11", limit, spent, threshold, null, Instant.EPOCH, Instant.EPOCH);
    }
}
