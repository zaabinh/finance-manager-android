package com.example.fintrack.app.core.di;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.fintrack.BuildConfig;
import com.example.fintrack.app.analytics.provider.AnalyticsDataProvider;
import com.example.fintrack.app.notification.provider.DemoNotificationProvider;

import org.junit.Test;

/** Runs in every flavor's unit tests: demo data exists only in mock; real flavors get null ("not connected"). */
public class DemoProvidersTest {
    @Test
    public void demoProvidersOnlyInMockFlavor() {
        AppContainer container = new AppContainer(null, new AppExecutors(Runnable::run, Runnable::run, Runnable::run, Runnable::run));
        if (BuildConfig.USE_MOCK) {
            assertTrue(container.analyticsProvider() instanceof AnalyticsDataProvider);
            assertTrue(container.notificationProvider() instanceof DemoNotificationProvider);
            assertSame(container.analyticsProvider(), container.analyticsProvider());
            assertSame(container.notificationProvider(), container.notificationProvider());
        } else {
            assertNull(container.analyticsProvider());
            assertNull(container.notificationProvider());
        }
    }
}
