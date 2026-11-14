package com.example.fintrack.app.home.remote;

/** Subset of the backend DashboardResponse used by the Home screen; integer VND minor units. */
public class DashboardResponse {
    public Assets assets;
    public Budget budget;

    public static class Assets {
        public long totalMinor, availableMinor, savingsMinor;
    }

    /** Aggregate of the month's category budgets; worstStatus is SAFE | WARNING | EXCEEDED. */
    public static class Budget {
        public int count, progressPercent;
        public long limitMinor, spentMinor, remainingMinor;
        public String worstStatus;
    }
}
