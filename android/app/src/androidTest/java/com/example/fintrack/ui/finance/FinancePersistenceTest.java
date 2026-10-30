package com.example.fintrack.ui.finance;

import static org.junit.Assert.*;
import android.content.Context;
import android.util.Base64;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.fintrack.app.finance.data.MockFinanceStore;
import com.example.fintrack.app.finance.domain.FinanceBook;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FinancePersistenceTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private File file(String owner) throws Exception {
        String name = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(owner.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP | Base64.URL_SAFE);
        return new File(context.getNoBackupFilesDir(), "finance/" + name + ".json");
    }
    @Test public void reloadAndEditDeleteKeepWalletAndBudgetConsistent() throws Exception {
        String owner = "finance-test-" + UUID.randomUUID();
        try {
            MockFinanceStore store = new MockFinanceStore(context, owner);
            String month = FinanceBook.month(System.currentTimeMillis());
            long baseline = store.read().spent(store.read().budget("demo-budget"));
            FinanceBook.Transaction transaction = new FinanceBook.Transaction("test", FinanceBook.Type.EXPENSE, 123_456, "food", "demo-cash", null, System.currentTimeMillis(), "", "Test");
            store.mutate(book -> book.save(transaction, false));
            FinanceBook reloaded = new MockFinanceStore(context, owner).read();
            assertEquals(baseline + 123_456, reloaded.budgetSpent(month));
            store.mutate(book -> book.deleteTransaction("test"));
            assertEquals(baseline, new MockFinanceStore(context, owner).read().budgetSpent(month));
        } finally { assertTrue(!file(owner).exists() || file(owner).delete()); }
    }
    @Test public void accountsAreIsolatedAndFailedValidationDoesNotWrite() throws Exception {
        String owner = "finance-test-" + UUID.randomUUID(), other = "finance-test-" + UUID.randomUUID();
        try {
            MockFinanceStore store = new MockFinanceStore(context, owner);
            store.mutate(book -> book.addWallet("Riêng tư", 55_000));
            int savedCount = store.read().state().wallets.size();
            assertEquals(savedCount - 1, new MockFinanceStore(context, other).read().state().wallets.size());
            assertThrows(FinanceBook.Failure.class, () -> store.mutate(book -> {
                book.addWallet("Không được ghi", 1);
                book.save(new FinanceBook.Budget("bad", "salary", FinanceBook.month(System.currentTimeMillis()), 1), false);
            }));
            assertEquals(savedCount, new MockFinanceStore(context, owner).read().state().wallets.size());
        } finally { assertTrue(!file(owner).exists() || file(owner).delete()); assertTrue(!file(other).exists() || file(other).delete()); }
    }
    @Test public void corruptStorageNeverFallsBackToDemoOrOverwritesTheFile() throws Exception {
        String owner = "finance-test-" + UUID.randomUUID();
        try {
            MockFinanceStore store = new MockFinanceStore(context, owner);
            store.mutate(book -> book.addWallet("Test", 1));
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(file(owner))) { output.write("{}".getBytes(StandardCharsets.UTF_8)); }
            assertThrows(FinanceBook.Failure.class, store::read);
            assertThrows(FinanceBook.Failure.class, () -> store.mutate(book -> book.addWallet("Lost", 1)));
            try (java.io.FileInputStream input = new java.io.FileInputStream(file(owner))) {
                byte[] bytes = new byte[2]; assertEquals(2, input.read(bytes)); assertEquals("{}", new String(bytes, StandardCharsets.UTF_8));
            }
        } finally { assertTrue(!file(owner).exists() || file(owner).delete()); }
    }
}
