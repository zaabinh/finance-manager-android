package com.example.fintrack.app.home.domain;

import static org.junit.Assert.*;

import com.example.fintrack.app.finance.domain.Budget;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.Transaction;
import com.example.fintrack.app.finance.domain.TransactionType;
import com.example.fintrack.app.finance.domain.WalletType;
import com.example.fintrack.app.home.remote.DashboardResponse;

import org.junit.Test;

import java.util.Calendar;
import java.util.Collections;
import java.util.UUID;

public class HomeSummaryTest {
    private static DashboardResponse response(long limit, long spent, int percent, String worst) {
        DashboardResponse body = new DashboardResponse();
        body.assets = new DashboardResponse.Assets();
        body.assets.totalMinor = 10_000_000; body.assets.availableMinor = 7_000_000; body.assets.savingsMinor = 3_000_000;
        body.budget = new DashboardResponse.Budget();
        body.budget.limitMinor = limit; body.budget.spentMinor = spent; body.budget.progressPercent = percent; body.budget.worstStatus = worst;
        return body;
    }

    private static long october() {
        Calendar calendar = Calendar.getInstance(FinanceBook.ZONE); calendar.clear(); calendar.set(2026, Calendar.OCTOBER, 15, 12, 0);
        return calendar.getTimeInMillis();
    }

    @Test public void serverSummaryIsMappedUnchanged() {
        HomeSummary summary = HomeSummary.fromDashboard(response(2_000_000, 500_000, 25, "SAFE"));
        assertEquals(10_000_000, summary.totalAssets); assertEquals(7_000_000, summary.available); assertEquals(3_000_000, summary.savings);
        assertEquals(2_000_000, summary.budgetLimit); assertEquals(500_000, summary.budgetSpent); assertEquals(25, summary.budgetPercent);
        assertFalse(summary.budgetExceeded); assertTrue(summary.hasBudget()); assertEquals(1_500_000, summary.budgetRemaining());
    }

    @Test public void serverWorstStatusOrTotalMarksExceeded() {
        assertTrue(HomeSummary.fromDashboard(response(2_000_000, 100, 5, "EXCEEDED")).budgetExceeded);
        assertTrue(HomeSummary.fromDashboard(response(2_000_000, 2_000_000, 100, "SAFE")).budgetExceeded);
    }

    @Test public void serverWithoutBudgetHasNoBudget() {
        HomeSummary summary = HomeSummary.fromDashboard(response(0, 300_000, 0, null));
        assertFalse(summary.hasBudget()); assertFalse(summary.budgetExceeded);
    }

    @Test public void ledgerExcludesSavingsFromAvailableAndDerivesBudget() {
        FinanceBook book = new FinanceBook(FinanceBook.empty());
        String cash = book.addWallet("Tiền mặt", 1_000_000).id;
        book.addWallet("Tiết kiệm", 4_000_000, WalletType.SAVINGS);
        book.save(new Transaction(UUID.randomUUID().toString(), TransactionType.EXPENSE, 200_000, "food", cash, null, october(), "", ""), false);
        book.createMonthlyBudget("m", "2026-10", Collections.singletonList(new Budget("b", "food", "2026-10", 400_000)));
        HomeSummary summary = HomeSummary.fromBook(book, october());
        assertEquals(4_800_000, summary.totalAssets); assertEquals(4_000_000, summary.savings); assertEquals(800_000, summary.available);
        assertEquals(400_000, summary.budgetLimit); assertEquals(200_000, summary.budgetSpent); assertEquals(50, summary.budgetPercent);
        assertFalse(summary.budgetExceeded);
    }

    @Test public void ledgerWithoutBudgetThisMonthHasZeroLimitAndPercent() {
        FinanceBook book = new FinanceBook(FinanceBook.empty());
        String cash = book.addWallet("Tiền mặt", 1_000_000).id;
        book.save(new Transaction(UUID.randomUUID().toString(), TransactionType.EXPENSE, 90_000, "food", cash, null, october(), "", ""), false);
        HomeSummary summary = HomeSummary.fromBook(book, october());
        assertFalse(summary.hasBudget()); assertEquals(0, summary.budgetPercent); assertFalse(summary.budgetExceeded);
        assertEquals(0, summary.budgetSpent); // no budget lines: only budgeted categories count (unchanged ledger rule)
    }

    @Test public void overflowIsNotSilentlyWrapped() {
        HomeSummary summary = new HomeSummary(0, 0, 0, Long.MIN_VALUE, 1, 0, false);
        assertThrows(ArithmeticException.class, summary::budgetRemaining);
    }
}
