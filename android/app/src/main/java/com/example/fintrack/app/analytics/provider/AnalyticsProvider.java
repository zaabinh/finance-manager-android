package com.example.fintrack.app.analytics.provider;

import com.example.fintrack.app.analytics.model.AnalyticsData;

/** Analytics figures for one month. Obtain it from AppContainer.analyticsProvider(); null there means "not connected". */
public interface AnalyticsProvider {
    /** {@code monthName} is the label the screen shows, e.g. "Tháng 10"; null means the default month. */
    AnalyticsData getAnalyticsDataForMonth(String monthName);
}
