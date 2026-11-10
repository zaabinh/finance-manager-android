package com.fintrack.api.dto.response.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Current assets and selected-month activity; all fields belong to one database snapshot. */
public record DashboardResponse(String month, String timeZone, String currencyCode, Instant snapshotAt,
        User user, Assets assets, MonthlyFlow monthlyFlow, Budget budget,
        List<RecentTransaction> recentTransactions, Notifications notifications) {
    public record User(String displayName) {}
    public record Assets(long totalMinor, long availableMinor, long savingsMinor, long walletCount) {}
    public record MonthlyFlow(long incomeMinor, long expenseMinor, long netSavingsMinor) {}
    public record Budget(int count, long limitMinor, long spentMinor, long remainingMinor,
            BigDecimal usagePercent, int progressPercent, String worstStatus, int warningCount, int exceededCount) {}
    public record RecentTransaction(UUID transactionId, String transactionType, long amountMinor, Instant occurredAt,
            Wallet wallet, Category category) {}
    public record Wallet(UUID walletId, String name, boolean archived) {}
    public record Category(UUID categoryId, String name, String iconKey, String colorHex, boolean archived) {}
    public record Notifications(boolean available, Long unreadCount) {}
}
