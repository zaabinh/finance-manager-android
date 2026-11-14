package com.example.fintrack.app.budget.ui;

import com.example.fintrack.app.finance.domain.Category;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.TransactionType;
import com.example.fintrack.app.finance.domain.Wallet;
import com.example.fintrack.app.finance.domain.WalletType;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.finance.ui.FinanceViewModel;
import com.example.fintrack.app.transaction.ui.TransactionFormActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Shared budget-screen helpers: category picker, server-code messages and the savings transfer shortcut. */
final class BudgetUi {
    // Transfer seam (spec §3). TransactionFormActivity owns the EXTRA_PREFILL_* constants; literals keep this
    // feature independent of the transfer work.
    static final String PREFILL_TYPE = "prefillType";
    static final String PREFILL_WALLET_ID = "prefillWalletId";
    static final String PREFILL_DESTINATION_WALLET_ID = "prefillDestinationWalletId";
    static final String PREFILL_AMOUNT_MINOR = "prefillAmountMinor";

    private BudgetUi() { }

    /** Active EXPENSE categories not yet used in this monthly budget (each category once). */
    static void pickExpenseCategory(Activity activity, FinanceBook book, Set<String> excluded, Consumer<String> selected) {
        if (book == null) { Toast.makeText(activity, R.string.finance_category_empty, Toast.LENGTH_SHORT).show(); return; }
        List<Category> options = new ArrayList<>();
        for (Category item : book.categories())
            if (item.type == TransactionType.EXPENSE && item.archivedAt == null && !excluded.contains(item.id)) options.add(item);
        if (options.isEmpty()) { Toast.makeText(activity, R.string.monthly_budget_all_categories_used, Toast.LENGTH_SHORT).show(); return; }
        CharSequence[] names = new CharSequence[options.size()];
        for (int i = 0; i < options.size(); i++) names[i] = options.get(i).name;
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.label_category)
                .setItems(names, (dialog, which) -> selected.accept(options.get(which).id)).show();
    }

    /**
     * Localised text for budget error codes. Shortfall shows the inline panel instead of a toast (returns null).
     */
    static CharSequence failure(Activity activity, FinanceBook book, FinanceViewModel.Command command, View panel) {
        String code = command.failure == null ? null : command.failure.code;
        if (FinanceBook.BUDGET_EXCEEDS_AVAILABLE_BALANCE.equals(code)) {
            long shortfall = command.failure.shortfallMinor == null ? 0 : command.failure.shortfallMinor;
            if (panel == null) return activity.getString(R.string.budget_shortfall, FinanceUi.money(shortfall));
            showShortfall(activity, book, shortfall, panel);
            return null;
        }
        if (FinanceBook.BUDGET_MONTH_MISSING.equals(code)) return activity.getString(R.string.budget_month_missing);
        if (FinanceBook.BUDGET_MONTH_CURRENT.equals(code)) return activity.getString(R.string.monthly_budget_current_locked);
        if (FinanceBook.BUDGET_ARCHIVED.equals(code)) return activity.getString(R.string.budget_archived_edit);
        if (FinanceBook.BUDGET_PERIOD_CONFLICT.equals(code)) return activity.getString(R.string.budget_category_exists);
        return null;
    }

    static void hideShortfall(View panel) { panel.setVisibility(View.GONE); }

    /** panel is the view_budget_shortfall root (included without an android:id, so it comes from the screen root). */
    static void showShortfall(Activity activity, FinanceBook book, long shortfall, View panel) {
        panel.setVisibility(View.VISIBLE);
        ((TextView) panel.findViewById(R.id.tvShortfall)).setText(activity.getString(R.string.budget_shortfall, FinanceUi.money(shortfall)));
        panel.findViewById(R.id.btnTransferFromSavings).setOnClickListener(view -> transferFromSavings(activity, book, shortfall));
    }

    /** Opens the transfer form prefilled: source = the active SAVINGS wallet with the largest balance, amount = shortfall. */
    static void transferFromSavings(Activity activity, FinanceBook book, long shortfall) {
        Wallet source = null;
        long best = Long.MIN_VALUE;
        if (book != null) for (Wallet wallet : book.wallets()) {
            if (wallet.archivedAt != null || wallet.walletType != WalletType.SAVINGS) continue;
            long balance = book.balance(wallet.id);
            if (balance > best) { best = balance; source = wallet; }
        }
        if (source == null) { Toast.makeText(activity, R.string.budget_no_savings_wallet, Toast.LENGTH_LONG).show(); return; }
        Intent intent = new Intent(activity, TransactionFormActivity.class)
                .putExtra(PREFILL_TYPE, TransactionType.TRANSFER.name())
                .putExtra(PREFILL_WALLET_ID, source.id);
        if (shortfall > 0) intent.putExtra(PREFILL_AMOUNT_MINOR, shortfall);
        activity.startActivity(intent);
    }
}
