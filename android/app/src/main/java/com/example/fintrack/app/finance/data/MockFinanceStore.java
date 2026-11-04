package com.example.fintrack.app.finance.data;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Base64;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.google.gson.Gson;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Consumer;

/** Persistent per-user mock data, used exclusively by the mock flavor. */
public final class MockFinanceStore {
    private final AtomicFile file;
    private final Gson gson = new Gson();

    public MockFinanceStore(Context context, String owner) {
        if (owner == null || owner.trim().isEmpty()) throw new IllegalArgumentException("Missing user");
        try {
            String key = Base64.encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(owner.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP | Base64.URL_SAFE);
            File directory = new File(context.getNoBackupFilesDir(), "finance");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Storage unavailable");
            file = new AtomicFile(new File(directory, key + ".json"));
        } catch (Exception e) { throw new FinanceBook.Failure(FinanceBook.Problem.STORAGE); }
    }

    /** Must run on IO, as must mutate(). Returned data is detached from persisted state. */
    public FinanceBook read() {
        if (!file.getBaseFile().exists() && !new File(file.getBaseFile().getPath() + ".bak").exists()) return demo();
        try {
            String json = new String(file.readFully(), StandardCharsets.UTF_8);
            com.google.gson.JsonObject object = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            for (String key : new String[]{"version", "wallets", "categories", "transactions", "budgets"})
                if (!object.has(key)) throw new IllegalStateException("Incomplete file");
            return new FinanceBook(gson.fromJson(object, FinanceBook.State.class));
        } catch (Exception e) { throw new FinanceBook.Failure(FinanceBook.Problem.STORAGE); }
    }

    public FinanceBook mutate(Consumer<FinanceBook> action) {
        FinanceBook candidate = read();
        action.accept(candidate);
        candidate.validateTotals();
        // Validate aggregate arithmetic before committing any change.
        for (FinanceBook.Wallet wallet : candidate.state().wallets) candidate.balance(wallet.id);
        FileOutputStream output = null;
        try {
            byte[] data = gson.toJson(candidate.state()).getBytes(StandardCharsets.UTF_8);
            output = file.startWrite();
            output.write(data);
            file.finishWrite(output);
            return candidate;
        } catch (Exception e) {
            if (output != null) file.failWrite(output);
            throw new FinanceBook.Failure(FinanceBook.Problem.STORAGE);
        }
    }
    private static FinanceBook demo() {
        FinanceBook.State state = FinanceBook.empty();
        state.wallets.add(new FinanceBook.Wallet("demo-cash", "Tiền mặt", 2_000_000));
        FinanceBook.Wallet bank = new FinanceBook.Wallet("demo-bank", "Ngân hàng", 10_000_000);
        bank.walletType = FinanceBook.WalletType.BANK; state.wallets.add(bank);
        FinanceBook book = new FinanceBook(state);
        long now = System.currentTimeMillis();
        book.save(new FinanceBook.Transaction("demo-expense", FinanceBook.Type.EXPENSE, 95_000, "food", "demo-cash", null, now, "Giao dịch mẫu", "Quán cà phê"), false);
        book.save(new FinanceBook.Transaction("demo-income", FinanceBook.Type.INCOME, 18_500_000, "salary", "demo-bank", null, now, "Giao dịch mẫu", ""), false);
        book.save(new FinanceBook.Budget("demo-budget", "food", FinanceBook.month(now), 3_000_000), false);
        return book;
    }
}
