package com.example.fintrack.app.finance.data;

import com.example.fintrack.app.finance.domain.TransactionType;
import com.example.fintrack.app.finance.domain.WalletType;
import java.util.ArrayList;
import java.util.List;

/** Stable version 1 disk schema. Domain models are never serialized by Gson. */
public final class FinanceFileDto {
    public int version=1;
    public String timeZone="Asia/Ho_Chi_Minh";
    public boolean serverSnapshot;
    public List<WalletDto> wallets=new ArrayList<>();
    public List<CategoryDto> categories=new ArrayList<>();
    public List<TransactionDto> transactions=new ArrayList<>();
    public List<BudgetDto> budgets=new ArrayList<>();
    public List<MonthlyBudgetDto> monthlyBudgets=new ArrayList<>();
    public static final class WalletDto {
        public String id;
        public String name;
        public long openingBalance;
        public WalletType walletType = WalletType.CASH;
        public String archivedAt;
        public boolean includeInAssets = true;
        public int sortOrder;
        public Long serverBalanceMinor;
    }
    public static final class CategoryDto {
        public String id;
        public String name;
        public TransactionType type;
        public String archivedAt;
        public String iconKey;
        public String colorHex;
        public int sortOrder;
    }
    public static final class TransactionDto {
        public String id;
        public TransactionType type;
        public long amount;
        public String categoryId;
        public String walletId;
        public String destinationWalletId;
        public long occurredAt;
        public String note;
        public String payee;
        public String status = "POSTED";
        public String voidedAt;
        public String clientRequestId;
    }
    public static final class BudgetDto {
        public String id;
        public String categoryId;
        public String month;
        public long limit;
        public Long serverSpentMinor;
        public int warningThresholdPercent = 80;
        public String archivedAt;
        public String monthlyBudgetId;
    }
    public static final class MonthlyBudgetDto {
        public String id;
        public String month;
        public String archivedAt;
        public Long serverTotalMinor;
        public Long serverSpentMinor;
        public int warningThresholdPercent = 80;
    }
}
