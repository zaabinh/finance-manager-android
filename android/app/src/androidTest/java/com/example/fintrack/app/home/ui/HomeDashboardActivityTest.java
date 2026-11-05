package com.example.fintrack.app.home.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.fintrack.R;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class HomeDashboardActivityTest {

    @Rule
    public ActivityScenarioRule<HomeDashboardActivity> activityRule =
            new ActivityScenarioRule<>(HomeDashboardActivity.class);

    @Test
    public void testDashboardViewsDisplayed() {
        onView(withId(R.id.tvTotalBalance)).check(matches(isDisplayed()));
        onView(withId(R.id.tvAvailableBalance)).check(matches(isDisplayed()));
        onView(withId(R.id.tvSavingsBalance)).check(matches(isDisplayed()));
    }

    @Test
    public void testToggleBalanceVisibility() {
        onView(withId(R.id.btnToggleBalanceVisibility)).perform(click());
    }
}
