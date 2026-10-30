package com.example.fintrack.app.transaction.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import java.util.List;

public final class TransactionListActivity extends FinanceActivity {
    private String month = FinanceBook.month(System.currentTimeMillis()), walletFilter, categoryFilter;
    private FinanceBook.Type type;
    @Override protected int layout() { return R.layout.activity_transaction_list; }
    @Override protected void bind(Bundle saved) {
        if (saved != null) {
            month = saved.getString("month", month); walletFilter = saved.getString("wallet"); categoryFilter = saved.getString("category");
        }
        FinanceUi.navigation(this, R.id.navTabHistory);
        findViewById(R.id.tvChangeMonth).setOnClickListener(view -> FinanceUi.month(this, month, value -> { month = value; render(); }));
        ((ChipGroup) findViewById(R.id.chipGroupTransactionType)).setOnCheckedStateChangeListener((group, ids) -> {
            int id = ids.isEmpty() ? R.id.chipFilterAll : ids.get(0);
            type = id == R.id.chipFilterExpense ? FinanceBook.Type.EXPENSE : id == R.id.chipFilterIncome ? FinanceBook.Type.INCOME
                    : id == R.id.chipFilterTransfer ? FinanceBook.Type.TRANSFER : null;
            render();
        });
        ((EditText) findViewById(R.id.etTransactionSearch)).addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { render(); }
            public void afterTextChanged(Editable value) { }
        });
        MaterialToolbar toolbar = findViewById(R.id.toolbarTransactions);
        toolbar.getMenu().add(R.string.finance_filters).setOnMenuItemClickListener(item -> {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setItems(
                    new String[]{getString(R.string.transaction_wallet_label), getString(R.string.transaction_category_label), getString(R.string.transaction_clear_filters)},
                    (dialog, which) -> {
                        if (which == 0) FinanceUi.wallet(this, value -> { walletFilter = value; render(); });
                        else if (which == 1) FinanceUi.category(this, type == FinanceBook.Type.INCOME ? type : FinanceBook.Type.EXPENSE,
                                value -> { categoryFilter = value; render(); });
                        else clearFilters();
                    }).show(); return true;
        });
        toolbar.getMenu().add(R.string.receipt_import_title).setOnMenuItemClickListener(item -> {
            startActivity(new Intent(this, ReceiptImportActivity.class)); return true;
        });
        findViewById(R.id.btnStatePrimary).setOnClickListener(view -> startActivity(new Intent(this, TransactionFormActivity.class)));
        findViewById(R.id.btnStateSecondary).setOnClickListener(view -> clearFilters());
    }
    private void clearFilters() {
        walletFilter = null; categoryFilter = null;
        ((EditText) findViewById(R.id.etTransactionSearch)).setText("");
        ((ChipGroup) findViewById(R.id.chipGroupTransactionType)).check(R.id.chipFilterAll); render();
    }
    @Override protected void render() {
        if (book == null) return;
        String query = ((EditText) findViewById(R.id.etTransactionSearch)).getText().toString();
        List<FinanceBook.Transaction> rows = book.transactions(month, type, query, walletFilter, categoryFilter);
        ((RecyclerView) findViewById(R.id.rvTransactions)).setAdapter(new TransactionAdapter(book, rows));
        text(R.id.tvTransactionMonth, getString(R.string.transaction_month_format, FinanceUi.monthLabel(month)));
        text(R.id.tvMonthSummary, getString(R.string.transaction_month_summary, FinanceUi.money(book.total(FinanceBook.Type.EXPENSE, month)),
                FinanceUi.money(book.total(FinanceBook.Type.INCOME, month))));
        boolean filtered = type != null || !query.trim().isEmpty() || walletFilter != null || categoryFilter != null;
        visible(R.id.stateEmpty, rows.isEmpty()); visible(R.id.rvTransactions, !rows.isEmpty());
        visible(R.id.btnStatePrimary, !filtered); visible(R.id.btnStateSecondary, filtered);
        text(R.id.btnStateSecondary, getString(R.string.transaction_clear_filters));
        text(R.id.tvStateTitle, getString(filtered ? R.string.transaction_no_results_title : R.string.transaction_empty_title));
        text(R.id.tvStateBody, filtered ? getString(R.string.finance_no_results) : getString(R.string.transaction_empty_body));
        String filters = (walletFilter == null || book.wallet(walletFilter) == null ? "" : book.wallet(walletFilter).name)
                + (categoryFilter == null || book.category(categoryFilter) == null ? "" : " · " + book.category(categoryFilter).name);
        text(R.id.tvActiveTransactionFilters, filters); visible(R.id.tvActiveTransactionFilters, !filters.isEmpty());
    }
    @Override protected void loading(boolean loading) { visible(R.id.stateLoadingList, loading && book == null); }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putString("month", month); out.putString("wallet", walletFilter); out.putString("category", categoryFilter);
    }
}
