package com.fintrack.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.api.dto.request.budget.BudgetRequest;
import com.fintrack.api.dto.response.budget.BudgetResponse;
import com.fintrack.api.exception.AppException;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.repository.BudgetRepository;
import com.fintrack.api.service.budget.BudgetService;
import com.fintrack.api.service.finance.FinanceSupport;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class BudgetServiceTest {
    private final BudgetRepository repository = mock(BudgetRepository.class);
    private final FinanceSupport finance = mock(FinanceSupport.class);
    private final BudgetService service = new BudgetService(repository, finance);
    private final UUID user = UUID.randomUUID(), category = UUID.randomUUID(), id = UUID.randomUUID();

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
        when(repository.lock(user, id)).thenReturn(new BudgetRepository.BudgetRow(id, category, true));
        when(finance.category(user, category, true)).thenReturn(new FinanceSupport.CategoryRef(category, "EXPENSE", false));
        service.update(user, id, request(100L));
        verify(repository).update(user, id, category, YearMonth.of(2026, 11), 100);
        assertThatThrownBy(() -> service.create(user, request(100L))).isInstanceOfSatisfying(AppException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CATEGORY_ARCHIVED));
    }
    @Test void rejectsIncomeCategoryInvalidLimitAndArchivedBudget() {
        when(finance.category(user, category, true)).thenReturn(new FinanceSupport.CategoryRef(category, "INCOME", true));
        assertThatThrownBy(() -> service.create(user, request(100L))).isInstanceOf(AppException.class);
        for (Long amount : new Long[]{null, 0L, -1L, 1_000_000_000_000L})
            assertThatThrownBy(() -> service.create(user, request(amount))).isInstanceOf(AppException.class);
        when(repository.lock(user, id)).thenReturn(new BudgetRepository.BudgetRow(id, category, false));
        assertThatThrownBy(() -> service.update(user, id, request(100L))).isInstanceOfSatisfying(AppException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.BUDGET_ARCHIVED));
        verify(repository, never()).create(any(), any(), any(), anyLong());
    }
    @Test void duplicateExclusionMapsTo409WithoutMaskingOtherDatabaseErrors() {
        when(finance.category(user, category, true)).thenReturn(new FinanceSupport.CategoryRef(category, "EXPENSE", true));
        when(repository.create(any(), any(), any(), anyLong())).thenThrow(new DataIntegrityViolationException("overlap", new SQLException("overlap", "23P01")));
        assertThatThrownBy(() -> service.create(user, request(100L))).isInstanceOfSatisfying(AppException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.BUDGET_PERIOD_CONFLICT));
        var other = new DataIntegrityViolationException("other", new SQLException("other", "23514"));
        doThrow(other).when(repository).create(any(), any(), any(), anyLong());
        assertThatThrownBy(() -> service.create(user, request(100L))).isSameAs(other);
    }
    @Test void archiveIsIdempotentAndNeverWritesLedger() {
        when(repository.lock(user, id)).thenReturn(new BudgetRepository.BudgetRow(id, category, true), new BudgetRepository.BudgetRow(id, category, false));
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
    private BudgetRequest request(Long amount) {
        BudgetRequest request = new BudgetRequest(); request.categoryId = category; request.month = "2026-11"; request.limitMinor = amount; return request;
    }
    private BudgetResponse usage(long limit, long spent, int threshold) {
        return BudgetResponse.of(id, category, "2026-11", limit, spent, threshold, null, Instant.EPOCH, Instant.EPOCH);
    }
}
