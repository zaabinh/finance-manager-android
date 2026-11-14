package com.example.fintrack.app.finance.data;

import static org.junit.Assert.*;
import com.example.fintrack.app.finance.domain.Budget;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.FinanceFailure;
import com.example.fintrack.app.finance.domain.FinanceProblem;
import com.example.fintrack.app.finance.domain.FinanceSnapshot;
import com.example.fintrack.app.finance.domain.MonthlyBudget;
import com.example.fintrack.app.finance.domain.Transaction;
import com.example.fintrack.app.finance.domain.TransactionType;
import com.example.fintrack.app.finance.domain.Wallet;
import com.example.fintrack.app.finance.domain.WalletType;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

public class FinanceFileCodecTest {
    private final FinanceFileCodec codec=new FinanceFileCodec();
    private String legacy(String walletExtra,String budgetExtra) {
        return "{\"version\":1,\"wallets\":[{\"id\":\"cash\",\"name\":\"Cash\",\"openingBalance\":1000"+walletExtra+"}],"
                +"\"categories\":[{\"id\":\"food\",\"name\":\"Food\",\"type\":\"EXPENSE\"}],\"transactions\":[],"
                +"\"budgets\":[{\"id\":\"b\",\"categoryId\":\"food\",\"month\":\"2026-10\",\"limit\":100"+budgetExtra+"}]}";
    }
    @Test public void readsVersionOneDefaultsAndAdoptsLegacyBudgets() {
        FinanceSnapshot result=codec.decode(legacy("",""));
        assertTrue(result.wallets.get(0).includeInAssets);
        assertEquals(WalletType.CASH,result.wallets.get(0).walletType);
        assertEquals(80,result.budgets.get(0).warningThresholdPercent);
        assertEquals(1,result.monthlyBudgets.size());
        assertEquals(result.monthlyBudgets.get(0).id,result.budgets.get(0).monthlyBudgetId);
        assertEquals("2026-10",result.monthlyBudgets.get(0).month);
    }
    @Test public void explicitFalseAndArchivedBudgetSurvive() {
        FinanceSnapshot result=codec.decode(legacy(",\"includeInAssets\":false",",\"archivedAt\":\"2026-11-01T00:00:00Z\""));
        assertFalse(result.wallets.get(0).includeInAssets);assertTrue(result.monthlyBudgets.isEmpty());
        assertNotNull(result.budgets.get(0).archivedAt);
    }
    @Test public void explicitEmptyMonthlyListDoesNotTriggerLegacyUpgrade() {
        JsonObject root=JsonParser.parseString(legacy("","")).getAsJsonObject();
        root.add("monthlyBudgets",new com.google.gson.JsonArray());
        FinanceSnapshot snapshot=codec.decode(root.toString());
        assertTrue(snapshot.monthlyBudgets.isEmpty());assertNull(snapshot.budgets.get(0).monthlyBudgetId);
    }
    @Test public void roundTripRetainsEveryLifecycleAndServerField() {
        FinanceSnapshot.Builder state=FinanceBook.empty();state.serverSnapshot=true;
        state.wallets.add(new Wallet("cash","Cash",1000,WalletType.BANK,"archived",false,7,0L));
        state.monthlyBudgets.add(new MonthlyBudget("m","2026-10","archived",200L,0L,90));
        state.budgets.add(new Budget("b","food","2026-10",200,0L,85,"archived","m"));
        state.transactions.add(new Transaction("t",TransactionType.EXPENSE,10,"food","cash",null,1," note "," payee ","VOIDED","voided","request"));
        FinanceSnapshot decoded=codec.decode(codec.encode(state.build()));
        Wallet wallet=decoded.wallets.get(0);assertFalse(wallet.includeInAssets);assertEquals(7,wallet.sortOrder);
        assertEquals(WalletType.BANK,wallet.walletType);assertEquals(Long.valueOf(0),wallet.serverBalanceMinor);
        assertEquals("archived",wallet.archivedAt);
        Transaction tx=decoded.transactions.get(0);assertEquals("request",tx.clientRequestId);
        assertEquals("VOIDED",tx.status);assertEquals("voided",tx.voidedAt);assertEquals("note",tx.note);assertEquals("payee",tx.payee);
        assertEquals("m",decoded.budgets.get(0).monthlyBudgetId);assertEquals(85,decoded.budgets.get(0).warningThresholdPercent);
        assertEquals(Long.valueOf(0),decoded.budgets.get(0).serverSpentMinor);
        assertEquals(Long.valueOf(200),decoded.monthlyBudgets.get(0).serverTotalMinor);
        assertEquals(Long.valueOf(0),decoded.monthlyBudgets.get(0).serverSpentMinor);
        assertTrue(decoded.serverSnapshot);
        assertEquals(JsonParser.parseString(codec.encode(decoded)),JsonParser.parseString(codec.encode(codec.decode(codec.encode(decoded)))));
    }
    @Test public void fractionalOverflowAndCorruptFilesAreStorageErrors() {
        for (String bad : new String[]{"{}","{broken",legacy("","").replace("1000","1.1"),
                legacy("","").replace("1000","9223372036854775808"),legacy("","").replace("\"cash\",\"name\"","null,\"name\"")}) {
            assertEquals(FinanceProblem.STORAGE,assertThrows(FinanceFailure.class,()->codec.decode(bad)).problem);
        }
    }
    @Test public void retainsLegacyRestorationNormalizationWithoutMutatingDto() {
        JsonObject file=JsonParser.parseString(legacy("","")).getAsJsonObject();
        file.getAsJsonArray("transactions").add(JsonParser.parseString("{\"id\":\"t\",\"type\":\"EXPENSE\",\"amount\":10,\"categoryId\":\"food\",\"walletId\":\"cash\",\"occurredAt\":0,\"note\":\" note \",\"payee\":null}"));
        Transaction tx=codec.decode(file.toString()).transactions.get(0);
        assertEquals("POSTED",tx.status);assertEquals("note",tx.note);assertEquals("",tx.payee);
        assertEquals(" note ",file.getAsJsonArray("transactions").get(0).getAsJsonObject().get("note").getAsString());
    }
}
