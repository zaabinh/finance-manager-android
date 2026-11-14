package com.example.fintrack.app.finance.data;

import com.example.fintrack.app.finance.domain.Budget;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.FinanceFailure;
import com.example.fintrack.app.finance.domain.FinanceProblem;
import com.example.fintrack.app.finance.domain.FinanceSnapshot;
import com.example.fintrack.app.finance.domain.Transaction;
import com.example.fintrack.app.finance.domain.TransactionType;
import com.example.fintrack.app.finance.domain.Wallet;
import com.example.fintrack.app.finance.domain.WalletType;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Base64;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Consumer;

/** Persistent per-user mock data, used exclusively by the mock flavor. */
public final class MockFinanceStore {
    private final AtomicFile file;
    private final FinanceFileCodec codec = new FinanceFileCodec();

    /** File boundary for isolated persistence tests; production files are always scoped by owner. */
    MockFinanceStore(AtomicFile file) { this.file = file; }

    public MockFinanceStore(Context context, String owner) {
        if (owner == null || owner.trim().isEmpty()) throw new IllegalArgumentException("Missing user");
        try {
            String key = Base64.encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(owner.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP | Base64.URL_SAFE);
            File directory = new File(context.getNoBackupFilesDir(), "finance");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Storage unavailable");
            file = new AtomicFile(new File(directory, key + ".json"));
        } catch (Exception e) { throw new FinanceFailure(FinanceProblem.STORAGE, e); }
    }

    /** Must run on IO, as must mutate(). Returned data is detached from persisted state. */
    public FinanceBook read() {
        if (!file.getBaseFile().exists() && !new File(file.getBaseFile().getPath() + ".bak").exists()) return demo();
        try {
            String json = new String(file.readFully(), StandardCharsets.UTF_8);
            return codec.decodeBook(json);
        } catch (Exception e) { throw new FinanceFailure(FinanceProblem.STORAGE, e); }
    }

    public FinanceBook mutate(Consumer<FinanceBook> action) {
        FinanceBook candidate = read();
        action.accept(candidate);
        candidate.validateTotals();
        // Validate aggregate arithmetic before committing any change.
        for (Wallet wallet : candidate.wallets())
            if (candidate.balance(wallet.id) < 0) throw new FinanceFailure(FinanceProblem.INSUFFICIENT_FUNDS);
        FileOutputStream output = null;
        try {
            byte[] data = codec.encode(candidate.snapshot()).getBytes(StandardCharsets.UTF_8);
            output = file.startWrite();
            output.write(data);
            file.finishWrite(output);
            return candidate;
        } catch (Exception e) {
            if (output != null) file.failWrite(output);
            throw new FinanceFailure(FinanceProblem.STORAGE, e);
        }
    }
    private static FinanceBook demo() {
        FinanceSnapshot.Builder state = FinanceBook.empty();
        state.wallets.add(new Wallet("demo-cash", "Tiền mặt", 2_000_000));
        Wallet bank = new Wallet("demo-bank", "Ngân hàng", 10_000_000);
        bank = bank.withWalletType(WalletType.BANK); state.wallets.add(bank);
        Wallet savings = new Wallet("demo-savings", "Tiết kiệm", 20_000_000);
        savings = savings.withWalletType(WalletType.SAVINGS); state.wallets.add(savings);
        FinanceBook book = new FinanceBook(state);
        long now = System.currentTimeMillis();
        book.save(new Transaction("demo-expense", TransactionType.EXPENSE, 95_000, "food", "demo-cash", null, now, "Giao dịch mẫu", "Quán cà phê"), false);
        book.save(new Transaction("demo-income", TransactionType.INCOME, 18_500_000, "salary", "demo-bank", null, now, "Giao dịch mẫu", ""), false);
        book.createMonthlyBudget("demo-monthly-budget", FinanceBook.month(now), java.util.Collections.singletonList(
                new Budget("demo-budget", "food", FinanceBook.month(now), 3_000_000)));
        return book;
    }
}
