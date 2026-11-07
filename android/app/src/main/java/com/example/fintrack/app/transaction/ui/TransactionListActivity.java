package com.example.fintrack.app.transaction.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fintrack.BuildConfig;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.transaction.adapter.TransactionAdapter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import java.util.List;

/** History: references come from the REFERENCES snapshot, rows from the server-paged query held by the ViewModel. */
public final class TransactionListActivity extends FinanceActivity {
    private String month = FinanceBook.month(System.currentTimeMillis()), walletFilter, categoryFilter;
    private FinanceBook.Type type;
    private TransactionAdapter adapter;
    @Override protected int layout() { return R.layout.activity_transaction_list; }
    @Override protected void bind(Bundle saved) {
        if (saved != null) {
            month = saved.getString("month", month); walletFilter = saved.getString("wallet"); categoryFilter = saved.getString("category");
        } else {
            // Entry points from wallet/budget detail.
            walletFilter = getIntent().getStringExtra("WALLET_ID"); categoryFilter = getIntent().getStringExtra("category_id");
            String requested = getIntent().getStringExtra("month");
            if (requested != null && requested.matches("[0-9]{4}-(0[1-9]|1[0-2])")) month = requested;
        }
        FinanceUi.navigation(this, R.id.navReport);
        visible(R.id.chipFilterTransfer, false); visible(R.id.tvMonthSummaryNote, false); // TRANSFER not supported yet
        // No aggregate endpoint: summing one page would be a partial (wrong) total, so the real flavor hides it.
        visible(R.id.tvMonthSummary, BuildConfig.USE_MOCK);
        findViewById(R.id.tvChangeMonth).setOnClickListener(view -> FinanceUi.month(this, month, value -> { month = value; search(); }));
        ((ChipGroup) findViewById(R.id.chipGroupTransactionType)).setOnCheckedStateChangeListener((group, ids) -> {
            int id = ids.isEmpty() ? R.id.chipFilterAll : ids.get(0);
            type = id == R.id.chipFilterExpense ? FinanceBook.Type.EXPENSE : id == R.id.chipFilterIncome ? FinanceBook.Type.INCOME : null;
            search();
        });
        ((EditText) findViewById(R.id.etTransactionSearch)).addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { search(); }
            public void afterTextChanged(Editable value) { }
        });
        MaterialToolbar toolbar = findViewById(R.id.toolbarTransactions);
        toolbar.getMenu().add(R.string.finance_filters).setOnMenuItemClickListener(item -> {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setItems(
                    new String[]{getString(R.string.transaction_wallet_label), getString(R.string.transaction_category_label), getString(R.string.transaction_clear_filters)},
                    (dialog, which) -> {
                        // Archived references are valid history filters.
                        if (which == 0) FinanceUi.wallet(this, false, value -> { walletFilter = value; search(); });
                        else if (which == 1) FinanceUi.category(this, type == FinanceBook.Type.INCOME ? type : FinanceBook.Type.EXPENSE, false,
                                value -> { categoryFilter = value; search(); });
                        else clearFilters();
                    }).show(); return true;
        });
        toolbar.getMenu().add(R.string.receipt_import_title).setOnMenuItemClickListener(item -> {
            startActivity(new Intent(this, TransactionImportActivity.class)); return true;
        });
        findViewById(R.id.btnStatePrimary).setOnClickListener(view -> startActivity(new Intent(this, TransactionFormActivity.class)));
        findViewById(R.id.btnStateSecondary).setOnClickListener(view -> clearFilters());
        ((RecyclerView) findViewById(R.id.rvTransactions)).addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                if (dy > 0 && !view.canScrollVertically(1)) model.more();
            }
        });
        if (model.query() == null) search(); // after rotation the ViewModel already holds the query and rows
    }
    private void search() {
        String query = ((EditText) findViewById(R.id.etTransactionSearch)).getText().toString();
        model.query(FinanceBook.Query.month(month, type, walletFilter, categoryFilter, query));
        render();
    }
    private void clearFilters() {
        walletFilter = null; categoryFilter = null;
        ((EditText) findViewById(R.id.etTransactionSearch)).setText("");
        ((ChipGroup) findViewById(R.id.chipGroupTransactionType)).check(R.id.chipFilterAll); search();
    }
    @Override protected void render() {
        if (book == null) return;
        List<FinanceBook.Transaction> rows = model.rows();
        if (adapter == null) { adapter = new TransactionAdapter(book, rows); ((RecyclerView) findViewById(R.id.rvTransactions)).setAdapter(adapter); }
        else adapter.submit(book, rows);
        text(R.id.tvTransactionMonth, FinanceUi.monthLabel(month));
        if (BuildConfig.USE_MOCK) text(R.id.tvMonthSummary, getString(R.string.transaction_month_summary,
                FinanceUi.money(book.total(FinanceBook.Type.EXPENSE, month)), FinanceUi.money(book.total(FinanceBook.Type.INCOME, month))));
        FinanceBook.Query query = model.query();
        boolean filtered = type != null || (query != null && query.q != null) || walletFilter != null || categoryFilter != null;
        visible(R.id.stateEmpty, rows.isEmpty() && !model.busy()); visible(R.id.rvTransactions, !rows.isEmpty());
        visible(R.id.btnStatePrimary, !filtered); visible(R.id.btnStateSecondary, filtered);
        text(R.id.btnStateSecondary, getString(R.string.transaction_clear_filters));
        text(R.id.tvStateTitle, getString(filtered ? R.string.transaction_no_results_title : R.string.transaction_empty_title));
        text(R.id.tvStateBody, filtered ? getString(R.string.finance_no_results) : getString(R.string.transaction_empty_body));
        String filters = (walletFilter == null || book.wallet(walletFilter) == null ? "" : book.wallet(walletFilter).name)
                + (categoryFilter == null || book.category(categoryFilter) == null ? "" : " · " + book.category(categoryFilter).name);
        text(R.id.tvActiveTransactionFilters, filters); visible(R.id.tvActiveTransactionFilters, !filters.isEmpty());
    }
    @Override protected void loading(boolean loading) { visible(R.id.stateLoadingList, loading && model.rows().isEmpty()); }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putString("month", month); out.putString("wallet", walletFilter); out.putString("category", categoryFilter);
    }
}
