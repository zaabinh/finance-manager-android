package com.example.fintrack.app.finance.data;

import com.example.fintrack.app.finance.domain.Budget;
import com.example.fintrack.app.finance.domain.Category;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.FinanceFailure;
import com.example.fintrack.app.finance.domain.FinanceProblem;
import com.example.fintrack.app.finance.domain.FinanceSnapshot;
import com.example.fintrack.app.finance.domain.MonthlyBudget;
import com.example.fintrack.app.finance.domain.Transaction;
import com.example.fintrack.app.finance.domain.Wallet;
import com.google.gson.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Explicit codec for the existing per-user mock file format. */
public final class FinanceFileCodec {
    private final Gson gson=new Gson();
    /** Decodes and validates a file; any malformed or inconsistent content is a STORAGE failure. */
    public FinanceSnapshot decode(String json) { return decodeBook(json).snapshot(); }

    /** Same as {@link #decode} but keeps the validated book, so the store does not rebuild it. */
    FinanceBook decodeBook(String json) {
        try {
            JsonObject root=JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("version") || root.get("version").getAsBigDecimal().intValueExact()!=1)
                throw new IllegalArgumentException("Unsupported finance file");
            for (String key : new String[]{"wallets","categories","transactions","budgets"})
                if (!root.has(key) || !root.get(key).isJsonArray()) throw new IllegalArgumentException("Incomplete file");
            // Validate integer fields before Gson can coerce fractional or overflowing numbers.
            validateNumbers(root,"wallets",new String[]{"openingBalance","serverBalanceMinor"},new String[]{"sortOrder"});
            validateNumbers(root,"categories",new String[]{},new String[]{"sortOrder"});
            validateNumbers(root,"transactions",new String[]{"amount","occurredAt"},new String[]{});
            validateNumbers(root,"budgets",new String[]{"limit","serverSpentMinor"},new String[]{"warningThresholdPercent"});
            validateNumbers(root,"monthlyBudgets",new String[]{"serverTotalMinor","serverSpentMinor"},new String[]{"warningThresholdPercent"});
            FinanceFileDto file=gson.fromJson(root,FinanceFileDto.class);
            if (!root.has("monthlyBudgets")) upgrade(file);
            return new FinanceBook(toSnapshot(file));
        } catch (RuntimeException error) { throw storage(error); }
    }
    public String encode(FinanceSnapshot snapshot) {
        FinanceFileDto file=new FinanceFileDto();
        file.version=snapshot.version; file.timeZone=snapshot.timeZone; file.serverSnapshot=snapshot.serverSnapshot;
        for (Wallet value : snapshot.wallets) {
            FinanceFileDto.WalletDto dto=new FinanceFileDto.WalletDto();
            dto.id=value.id;
            dto.name=value.name;
            dto.openingBalance=value.openingBalance;
            dto.walletType=value.walletType;
            dto.archivedAt=value.archivedAt;
            dto.includeInAssets=value.includeInAssets;
            dto.sortOrder=value.sortOrder;
            dto.serverBalanceMinor=value.serverBalanceMinor;
            file.wallets.add(dto);
        }
        for (Category value : snapshot.categories) {
            FinanceFileDto.CategoryDto dto=new FinanceFileDto.CategoryDto();
            dto.id=value.id;
            dto.name=value.name;
            dto.type=value.type;
            dto.archivedAt=value.archivedAt;
            dto.iconKey=value.iconKey;
            dto.colorHex=value.colorHex;
            dto.sortOrder=value.sortOrder;
            file.categories.add(dto);
        }
        for (Transaction value : snapshot.transactions) {
            FinanceFileDto.TransactionDto dto=new FinanceFileDto.TransactionDto();
            dto.id=value.id;
            dto.type=value.type;
            dto.amount=value.amount;
            dto.categoryId=value.categoryId;
            dto.walletId=value.walletId;
            dto.destinationWalletId=value.destinationWalletId;
            dto.occurredAt=value.occurredAt;
            dto.note=value.note;
            dto.payee=value.payee;
            dto.status=value.status;
            dto.voidedAt=value.voidedAt;
            dto.clientRequestId=value.clientRequestId;
            file.transactions.add(dto);
        }
        for (Budget value : snapshot.budgets) {
            FinanceFileDto.BudgetDto dto=new FinanceFileDto.BudgetDto();
            dto.id=value.id;
            dto.categoryId=value.categoryId;
            dto.month=value.month;
            dto.limit=value.limit;
            dto.serverSpentMinor=value.serverSpentMinor;
            dto.warningThresholdPercent=value.warningThresholdPercent;
            dto.archivedAt=value.archivedAt;
            dto.monthlyBudgetId=value.monthlyBudgetId;
            file.budgets.add(dto);
        }
        for (MonthlyBudget value : snapshot.monthlyBudgets) {
            FinanceFileDto.MonthlyBudgetDto dto=new FinanceFileDto.MonthlyBudgetDto();
            dto.id=value.id;
            dto.month=value.month;
            dto.archivedAt=value.archivedAt;
            dto.serverTotalMinor=value.serverTotalMinor;
            dto.serverSpentMinor=value.serverSpentMinor;
            dto.warningThresholdPercent=value.warningThresholdPercent;
            file.monthlyBudgets.add(dto);
        }
        return gson.toJson(file);
    }
    private static FinanceSnapshot toSnapshot(FinanceFileDto file) {
        FinanceSnapshot.Builder result=new FinanceSnapshot.Builder();
        result.version=file.version;result.timeZone=file.timeZone;result.serverSnapshot=file.serverSnapshot;
        for (FinanceFileDto.WalletDto dto : file.wallets) result.wallets.add(new Wallet(dto.id, dto.name, dto.openingBalance, dto.walletType, dto.archivedAt, dto.includeInAssets, dto.sortOrder, dto.serverBalanceMinor));
        for (FinanceFileDto.CategoryDto dto : file.categories) result.categories.add(new Category(dto.id, dto.name, dto.type, dto.archivedAt, dto.iconKey, dto.colorHex, dto.sortOrder));
        for (FinanceFileDto.TransactionDto dto : file.transactions) result.transactions.add(new Transaction(dto.id, dto.type, dto.amount, dto.categoryId, dto.walletId, dto.destinationWalletId, dto.occurredAt, dto.note, dto.payee, dto.status, dto.voidedAt, dto.clientRequestId));
        for (FinanceFileDto.BudgetDto dto : file.budgets) result.budgets.add(new Budget(dto.id, dto.categoryId, dto.month, dto.limit, dto.serverSpentMinor, dto.warningThresholdPercent, dto.archivedAt, dto.monthlyBudgetId));
        if (file.monthlyBudgets!=null) for (FinanceFileDto.MonthlyBudgetDto dto : file.monthlyBudgets) result.monthlyBudgets.add(new MonthlyBudget(dto.id, dto.month, dto.archivedAt, dto.serverTotalMinor, dto.serverSpentMinor, dto.warningThresholdPercent));
        return result.build();
    }
    private static void upgrade(FinanceFileDto file) {
        file.monthlyBudgets=new ArrayList<>();
        Map<String,String> parents=new HashMap<>();
        for (FinanceFileDto.BudgetDto budget : file.budgets) {
            if (budget==null || budget.archivedAt!=null || budget.month==null) continue;
            String parent=parents.get(budget.month);
            if (parent==null) {
                parent=UUID.randomUUID().toString();parents.put(budget.month,parent);
                FinanceFileDto.MonthlyBudgetDto monthly=new FinanceFileDto.MonthlyBudgetDto();
                monthly.id=parent;monthly.month=budget.month;file.monthlyBudgets.add(monthly);
            }
            budget.monthlyBudgetId=parent;
        }
    }
    private static void validateNumbers(JsonObject root,String key,String[] longs,String[] ints) {
        if (!root.has(key) || root.get(key).isJsonNull()) return;
        for (JsonElement item : root.getAsJsonArray(key)) {
            JsonObject object=item.getAsJsonObject();
            for (String name : longs) {
                JsonElement value=object.get(name);
                if (value!=null && !value.isJsonNull()) {
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Invalid integer");
                    value.getAsBigDecimal().longValueExact();
                }
            }
            for (String name : ints) {
                JsonElement value=object.get(name);
                if (value!=null && !value.isJsonNull()) {
                    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Invalid integer");
                    value.getAsBigDecimal().intValueExact();
                }
            }
        }
    }
    private static FinanceFailure storage(RuntimeException cause) {
        FinanceFailure failure=new FinanceFailure(FinanceProblem.STORAGE);failure.initCause(cause);return failure;
    }
}
