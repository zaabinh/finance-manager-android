package com.example.fintrack.app.transaction.ui;

import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.Transaction;
import com.example.fintrack.app.finance.domain.TransactionType;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
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
import com.example.fintrack.databinding.ActivityTransactionListBinding;
import java.util.List;

/** History: references come from the REFERENCES snapshot, rows from the server-paged query held by the ViewModel. */
public final class TransactionListActivity extends FinanceActivity {
    private String month = FinanceBook.month(System.currentTimeMillis()), walletFilter, categoryFilter;
    private TransactionType type;
    private TransactionAdapter adapter;
    private boolean explicitMonth;
    private TransactionListViewModel history;
    private boolean firstResume = true, updatingFilters;
    private ActivityTransactionListBinding binding;
    @Override protected View contentView() { binding = ActivityTransactionListBinding.inflate(getLayoutInflater()); return binding.getRoot(); }
    @Override protected void bind(Bundle saved) {
        history = new ViewModelProvider(this, FintrackApplication.container(this).viewModelFactory())
                .get(TransactionListViewModel.class);
        if (saved != null) {
            month = saved.getString("month", month); walletFilter = saved.getString("wallet"); categoryFilter = saved.getString("category");
            explicitMonth = saved.getBoolean("explicitMonth");
            String savedType = saved.getString("type");
            type = savedType == null ? null : TransactionType.valueOf(savedType);
            binding.etTransactionSearch.setText(saved.getString("search", ""));
        } else {
            // Entry points from wallet/budget detail.
            walletFilter = getIntent().getStringExtra("WALLET_ID"); categoryFilter = getIntent().getStringExtra("category_id");
            String requested = getIntent().getStringExtra("month");
            if (requested != null && requested.matches("[0-9]{4}-(0[1-9]|1[0-2])")) { month = requested; explicitMonth = true; }
        }
        FinanceUi.navigation(this, R.id.navReport);
        // No aggregate endpoint: summing one page would be a partial (wrong) total, so the real flavor hides it.
        // The mock income/expense summary never includes transfers; the note says so.
        FinanceUi.visible(binding.tvMonthSummary, BuildConfig.USE_MOCK); FinanceUi.visible(binding.tvMonthSummaryNote, BuildConfig.USE_MOCK);
        binding.tvChangeMonth.setOnClickListener(view -> FinanceUi.month(this, month, value -> { month = value; explicitMonth = true; search(); }));
        binding.chipGroupTransactionType.check(type == TransactionType.INCOME ? R.id.chipFilterIncome
                : type == TransactionType.EXPENSE ? R.id.chipFilterExpense
                : type == TransactionType.TRANSFER ? R.id.chipFilterTransfer : R.id.chipFilterAll);
        binding.chipGroupTransactionType.setOnCheckedStateChangeListener((group, ids) -> {
            if (updatingFilters) return;
            int id = ids.isEmpty() ? R.id.chipFilterAll : ids.get(0);
            type = id == R.id.chipFilterExpense ? TransactionType.EXPENSE : id == R.id.chipFilterIncome ? TransactionType.INCOME
                    : id == R.id.chipFilterTransfer ? TransactionType.TRANSFER : null;
            search();
        });
        binding.etTransactionSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { if (!updatingFilters) search(); }
            public void afterTextChanged(Editable value) { }
        });
        MaterialToolbar toolbar = binding.toolbarTransactions;
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
        binding.stateEmpty.btnStatePrimary.setOnClickListener(view -> startActivity(new Intent(this, TransactionFormActivity.class)));
        binding.stateEmpty.btnStateSecondary.setOnClickListener(view -> clearFilters());
        binding.rvTransactions.addOnScrollListener(new RecyclerView.OnScrollListener() {
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
        String query = binding.etTransactionSearch.getText().toString();
        history.query(FinanceBook.Query.month(month, type, walletFilter, categoryFilter, query));
        render();
    }
    private void clearFilters() {
        walletFilter = null; categoryFilter = null; type = null;
        updatingFilters = true;
        binding.etTransactionSearch.setText("");
        binding.chipGroupTransactionType.check(R.id.chipFilterAll);
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
        if (adapter == null) { adapter = new TransactionAdapter(book, rows); binding.rvTransactions.setAdapter(adapter); }
        else adapter.submit(book, rows);
        binding.tvTransactionMonth.setText(FinanceUi.monthLabel(month));
        if (BuildConfig.USE_MOCK) binding.tvMonthSummary.setText(getString(R.string.transaction_month_summary,
                FinanceUi.money(book.total(TransactionType.EXPENSE, month)), FinanceUi.money(book.total(TransactionType.INCOME, month))));
        FinanceBook.Query query = history.query();
        boolean filtered = type != null || (query != null && query.q != null) || walletFilter != null || categoryFilter != null;
        FinanceUi.visible(binding.stateEmpty.getRoot(), rows.isEmpty() && !history.initialLoading()
                && (history.state().getValue() == null || history.state().getValue().getStatus() != Resource.Status.ERROR)); FinanceUi.visible(binding.rvTransactions, !rows.isEmpty());
        FinanceUi.visible(binding.stateEmpty.btnStatePrimary, !filtered); FinanceUi.visible(binding.stateEmpty.btnStateSecondary, filtered);
        binding.stateEmpty.btnStateSecondary.setText(getString(R.string.transaction_clear_filters));
        binding.stateEmpty.tvStateTitle.setText(getString(filtered ? R.string.transaction_no_results_title : R.string.transaction_empty_title));
        binding.stateEmpty.tvStateBody.setText(filtered ? getString(R.string.finance_no_results) : getString(R.string.transaction_empty_body));
        String filters = (walletFilter == null || book.wallet(walletFilter) == null ? "" : book.wallet(walletFilter).name)
                + (categoryFilter == null || book.category(categoryFilter) == null ? "" : " · " + book.category(categoryFilter).name);
        binding.tvActiveTransactionFilters.setText(filters); FinanceUi.visible(binding.tvActiveTransactionFilters, !filters.isEmpty());
    }
    @Override protected void loading(boolean loading) { updateLoading(); }
    private void updateLoading() {
        FinanceUi.visible(binding.stateLoadingList.getRoot(), history != null && history.initialLoading() && history.rows().isEmpty());
    }
    @Override protected void onResume() {
        super.onResume();
        if (firstResume) firstResume = false;
        else if (!isFinishing() && history != null) history.refresh();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putString("month", month); out.putString("wallet", walletFilter); out.putString("category", categoryFilter);
        out.putBoolean("explicitMonth", explicitMonth); out.putString("type", type == null ? null : type.name());
        out.putString("search", binding.etTransactionSearch.getText().toString());
    }
}
