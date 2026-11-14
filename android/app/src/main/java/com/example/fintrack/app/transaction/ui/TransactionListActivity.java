package com.example.fintrack.app.transaction.ui;

import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.Transaction;
import com.example.fintrack.app.finance.domain.TransactionType;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import com.example.fintrack.app.FintrackApplication;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.finance.ui.FinanceErrors;
import com.example.fintrack.app.transaction.viewmodel.TransactionListViewModel;
import com.google.android.material.snackbar.Snackbar;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fintrack.BuildConfig;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.transaction.adapter.TransactionAdapter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import java.util.List;

/** History: references come from the REFERENCES snapshot, rows from the server-paged query held by the ViewModel. */
public final class TransactionListActivity extends FinanceActivity {
    private String month = FinanceBook.month(System.currentTimeMillis()), walletFilter, categoryFilter;
    private TransactionType type;
    private TransactionAdapter adapter;
    private boolean explicitMonth;
    private TransactionListViewModel history;
    private boolean firstResume = true, updatingFilters;
    @Override protected int layout() { return R.layout.activity_transaction_list; }
    @Override protected void bind(Bundle saved) {
        history = new ViewModelProvider(this, FintrackApplication.container(this).viewModelFactory())
                .get(TransactionListViewModel.class);
        if (saved != null) {
            month = saved.getString("month", month); walletFilter = saved.getString("wallet"); categoryFilter = saved.getString("category");
            explicitMonth = saved.getBoolean("explicitMonth");
            String savedType = saved.getString("type");
            type = savedType == null ? null : TransactionType.valueOf(savedType);
            ((EditText) findViewById(R.id.etTransactionSearch)).setText(saved.getString("search", ""));
        } else {
            // Entry points from wallet/budget detail.
            walletFilter = getIntent().getStringExtra("WALLET_ID"); categoryFilter = getIntent().getStringExtra("category_id");
            String requested = getIntent().getStringExtra("month");
            if (requested != null && requested.matches("[0-9]{4}-(0[1-9]|1[0-2])")) { month = requested; explicitMonth = true; }
        }
        FinanceUi.navigation(this, R.id.navReport);
        // No aggregate endpoint: summing one page would be a partial (wrong) total, so the real flavor hides it.
        // The mock income/expense summary never includes transfers; the note says so.
        visible(R.id.tvMonthSummary, BuildConfig.USE_MOCK); visible(R.id.tvMonthSummaryNote, BuildConfig.USE_MOCK);
        findViewById(R.id.tvChangeMonth).setOnClickListener(view -> FinanceUi.month(this, month, value -> { month = value; explicitMonth = true; search(); }));
        ((ChipGroup) findViewById(R.id.chipGroupTransactionType)).check(type == TransactionType.INCOME ? R.id.chipFilterIncome
                : type == TransactionType.EXPENSE ? R.id.chipFilterExpense
                : type == TransactionType.TRANSFER ? R.id.chipFilterTransfer : R.id.chipFilterAll);
        ((ChipGroup) findViewById(R.id.chipGroupTransactionType)).setOnCheckedStateChangeListener((group, ids) -> {
            if (updatingFilters) return;
            int id = ids.isEmpty() ? R.id.chipFilterAll : ids.get(0);
            type = id == R.id.chipFilterExpense ? TransactionType.EXPENSE : id == R.id.chipFilterIncome ? TransactionType.INCOME
                    : id == R.id.chipFilterTransfer ? TransactionType.TRANSFER : null;
            search();
        });
        ((EditText) findViewById(R.id.etTransactionSearch)).addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { if (!updatingFilters) search(); }
            public void afterTextChanged(Editable value) { }
        });
        MaterialToolbar toolbar = findViewById(R.id.toolbarTransactions);
        toolbar.getMenu().add(R.string.finance_filters).setOnMenuItemClickListener(item -> {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setItems(
                    new String[]{getString(R.string.transaction_wallet_label), getString(R.string.transaction_category_label), getString(R.string.transaction_clear_filters)},
                    (dialog, which) -> {
                        // Archived references are valid history filters.
                        if (which == 0) FinanceUi.wallet(this, false, value -> { walletFilter = value; search(); });
                        else if (which == 1) FinanceUi.category(this, type == TransactionType.INCOME ? type : TransactionType.EXPENSE, false,
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
                if (dy > 0 && !view.canScrollVertically(1)) history.more();
            }
        });
        history.state().observe(this, resource -> {
            if (resource.getStatus() == Resource.Status.ERROR && !authenticated()) return; // session ended: Login
            updateLoading();
            render();
            if (resource.getStatus() == Resource.Status.ERROR) {
                Snackbar.make(findViewById(android.R.id.content), FinanceUi.error(FinanceErrors.problem(resource.getError())),
                        Snackbar.LENGTH_INDEFINITE).setAction(R.string.action_retry, view -> history.refresh()).show();
            }
        });
        history.loadMoreError().observe(this, error -> {
            updateLoading();
            if (error != null) Snackbar.make(findViewById(android.R.id.content), FinanceUi.error(FinanceErrors.problem(error)),
                    Snackbar.LENGTH_INDEFINITE).setAction(R.string.action_retry, view -> history.more()).show();
        });
        if (history.query() == null) search(); // after rotation the ViewModel already holds the query and rows
    }
    private void search() {
        String query = ((EditText) findViewById(R.id.etTransactionSearch)).getText().toString();
        history.query(FinanceBook.Query.month(month, type, walletFilter, categoryFilter, query));
        render();
    }
    private void clearFilters() {
        walletFilter = null; categoryFilter = null; type = null;
        updatingFilters = true;
        ((EditText) findViewById(R.id.etTransactionSearch)).setText("");
        ((ChipGroup) findViewById(R.id.chipGroupTransactionType)).check(R.id.chipFilterAll);
        updatingFilters = false;
        search();
    }
    @Override protected void render() {
        if (book == null) return;
        if (!explicitMonth) {
            String currentMonth = book.monthAt(System.currentTimeMillis());
            if (!currentMonth.equals(month)) { month = currentMonth; search(); return; }
        }
        List<Transaction> rows = history.rows();
        if (adapter == null) { adapter = new TransactionAdapter(book, rows); ((RecyclerView) findViewById(R.id.rvTransactions)).setAdapter(adapter); }
        else adapter.submit(book, rows);
        text(R.id.tvTransactionMonth, FinanceUi.monthLabel(month));
        if (BuildConfig.USE_MOCK) text(R.id.tvMonthSummary, getString(R.string.transaction_month_summary,
                FinanceUi.money(book.total(TransactionType.EXPENSE, month)), FinanceUi.money(book.total(TransactionType.INCOME, month))));
        FinanceBook.Query query = history.query();
        boolean filtered = type != null || (query != null && query.q != null) || walletFilter != null || categoryFilter != null;
        visible(R.id.stateEmpty, rows.isEmpty() && !history.initialLoading()
                && (history.state().getValue() == null || history.state().getValue().getStatus() != Resource.Status.ERROR)); visible(R.id.rvTransactions, !rows.isEmpty());
        visible(R.id.btnStatePrimary, !filtered); visible(R.id.btnStateSecondary, filtered);
        text(R.id.btnStateSecondary, getString(R.string.transaction_clear_filters));
        text(R.id.tvStateTitle, getString(filtered ? R.string.transaction_no_results_title : R.string.transaction_empty_title));
        text(R.id.tvStateBody, filtered ? getString(R.string.finance_no_results) : getString(R.string.transaction_empty_body));
        String filters = (walletFilter == null || book.wallet(walletFilter) == null ? "" : book.wallet(walletFilter).name)
                + (categoryFilter == null || book.category(categoryFilter) == null ? "" : " · " + book.category(categoryFilter).name);
        text(R.id.tvActiveTransactionFilters, filters); visible(R.id.tvActiveTransactionFilters, !filters.isEmpty());
    }
    @Override protected void loading(boolean loading) { updateLoading(); }
    private void updateLoading() {
        visible(R.id.stateLoadingList, history != null && history.initialLoading() && history.rows().isEmpty());
    }
    @Override protected void onResume() {
        super.onResume();
        if (firstResume) firstResume = false;
        else if (!isFinishing() && history != null) history.refresh();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putString("month", month); out.putString("wallet", walletFilter); out.putString("category", categoryFilter);
        out.putBoolean("explicitMonth", explicitMonth); out.putString("type", type == null ? null : type.name());
        out.putString("search", ((EditText) findViewById(R.id.etTransactionSearch)).getText().toString());
    }
}
