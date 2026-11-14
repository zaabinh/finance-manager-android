package com.example.fintrack.ui.finance;

import com.example.fintrack.app.finance.domain.FinanceBook;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;
import android.content.Context;
import android.content.Intent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.fintrack.R;
import com.example.fintrack.app.core.session.SessionSnapshot;
import com.example.fintrack.app.core.session.SessionStore;
import com.example.fintrack.app.FintrackApplication;
import com.example.fintrack.app.finance.data.MockFinanceStore;
import com.example.fintrack.app.transaction.ui.TransactionFormActivity;
import com.example.fintrack.app.budget.ui.BudgetFormActivity;
import com.example.fintrack.app.budget.ui.BudgetListActivity;
import com.example.fintrack.app.transaction.ui.TransactionListActivity;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FinanceFlowTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private SessionStore session;
    private String owner;
    private SessionSnapshot old;
    @Before public void login() {
        session = FintrackApplication.container(context).sessionStore(); old = session.current();
        owner = "finance-flow-" + UUID.randomUUID();
        session.updateProfile(session.login("fixture", "fixture", true, true), owner, "fixture@example.invalid", "Test");
    }
    @After public void restoreSession() throws Exception {
        drain();
        // The store has no unconditional writes: the previous state is restored as a new login (or a cleared one).
        SessionSnapshot restored = session.login(old.accessToken, old.refreshToken, old.setupCompleted, true);
        if (old.loggedIn) session.updateProfile(restored, old.userId, old.email, old.name);
        else session.clear(restored);
        String key = android.util.Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256")
                .digest(owner.getBytes(java.nio.charset.StandardCharsets.UTF_8)), android.util.Base64.NO_WRAP | android.util.Base64.URL_SAFE);
        java.io.File file = new java.io.File(context.getNoBackupFilesDir(), "finance/" + key + ".json");
        assertTrue(!file.exists() || file.delete());
    }
    private void drain() throws Exception {
        java.util.concurrent.CountDownLatch idle = new java.util.concurrent.CountDownLatch(1);
        FintrackApplication.container(context).executors().serial().execute(idle::countDown);
        assertTrue(idle.await(20, TimeUnit.SECONDS));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    @Test public void expenseEditAndRotationPreserveTheDraftAndRecalculateBudget() throws Exception {
        MockFinanceStore store = new MockFinanceStore(context, owner);
        long before = store.read().spent(store.read().budget("demo-budget"));
        try (ActivityScenario<TransactionFormActivity> scenario = ActivityScenario.launch(new Intent(context, TransactionFormActivity.class)
                .putExtra(TransactionFormActivity.EXTRA_ID, "demo-expense"))) {
            drain();
            onView(withId(R.id.etAmount)).perform(replaceText("125000"), closeSoftKeyboard());
            scenario.recreate(); drain();
            onView(withId(R.id.etAmount)).check(matches(withText("125000")));
            onView(withId(R.id.btnSaveTransaction)).perform(click()); drain();
        }
        assertEquals(125_000, store.read().transaction("demo-expense").amount);
        assertEquals(before + 30_000, store.read().spent(store.read().budget("demo-budget")));
    }
    @Test public void budgetEditSavesAndRejectsZeroAmount() throws Exception {
        try (ActivityScenario<BudgetFormActivity> scenario = ActivityScenario.launch(new Intent(context, BudgetFormActivity.class)
                .putExtra(BudgetFormActivity.EXTRA_ID, "demo-budget"))) {
            drain();
            onView(withId(R.id.etAmount)).perform(replaceText("0"), closeSoftKeyboard());
            onView(withId(R.id.btnSaveBudget)).perform(click());
            onView(withId(R.id.tvAmountError)).check(matches(isDisplayed()));
            onView(withId(R.id.etAmount)).perform(replaceText("4500000"), closeSoftKeyboard());
            onView(withId(R.id.btnSaveBudget)).perform(click()); drain();
        }
        assertEquals(4_500_000, new MockFinanceStore(context, owner).read().budget("demo-budget").limit);
    }
    @Test public void rotationDuringInitialLoadStillPopulatesTheEditForm() throws Exception {
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        FintrackApplication.container(context).executors().serial().execute(() -> {
            try { release.await(20, TimeUnit.SECONDS); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
        });
        try (ActivityScenario<BudgetFormActivity> scenario = ActivityScenario.launch(new Intent(context, BudgetFormActivity.class)
                .putExtra(BudgetFormActivity.EXTRA_ID, "demo-budget"))) {
            scenario.recreate(); release.countDown(); drain();
            onView(withId(R.id.etAmount)).check(matches(withText("3000000")));
        } finally { release.countDown(); }
    }
    @Test public void sameWalletTransferCannotBeSaved() throws Exception {
        try (ActivityScenario<TransactionFormActivity> scenario = ActivityScenario.launch(TransactionFormActivity.class)) {
            drain();
            onView(withId(R.id.btnTypeTransfer)).perform(click());
            onView(withId(R.id.fieldDestinationWallet)).perform(scrollTo(), click());
            onView(withText(org.hamcrest.Matchers.startsWith("Tiền mặt ·"))).perform(click());
            onView(withId(R.id.feedbackSameWallet)).check(matches(isDisplayed()));
            onView(withId(R.id.etAmount)).perform(scrollTo(), replaceText("10000"), closeSoftKeyboard());
            onView(withId(R.id.btnSaveTransaction)).perform(click()); drain();
            assertEquals(2, new MockFinanceStore(context, owner).read().snapshot().transactions.size());
        }
    }
    @Test public void transactionListOpensDetailAndDeleteReversesTheExpense() throws Exception {
        try (ActivityScenario<TransactionListActivity> scenario = ActivityScenario.launch(TransactionListActivity.class)) {
            drain();
            onView(withText("Quán cà phê")).perform(click()); drain();
            onView(withId(R.id.tvDetailAmount)).check(matches(withText("−95.000\u00a0đ")));
            onView(withId(R.id.btnDeleteTransaction)).perform(click());
            onView(withId(android.R.id.button1)).perform(click()); drain();
            FinanceBook book = new MockFinanceStore(context, owner).read();
            assertNull(book.transaction("demo-expense")); assertEquals(0, book.spent(book.budget("demo-budget")));
        }
    }
    @Test public void budgetListOpensDetailAndDeletingBudgetKeepsTransactions() throws Exception {
        try (ActivityScenario<BudgetListActivity> scenario = ActivityScenario.launch(BudgetListActivity.class)) {
            drain();
            onView(withId(R.id.cardBudget)).perform(click()); drain();
            onView(withId(R.id.tvDetailSpentAmount)).check(matches(withText("95.000\u00a0đ")));
            onView(withId(R.id.btnDeleteBudget)).perform(click());
            onView(withId(android.R.id.button1)).perform(click()); drain();
            FinanceBook book = new MockFinanceStore(context, owner).read();
            assertNull(book.budget("demo-budget")); assertEquals(2, book.snapshot().transactions.size());
        }
    }
}
