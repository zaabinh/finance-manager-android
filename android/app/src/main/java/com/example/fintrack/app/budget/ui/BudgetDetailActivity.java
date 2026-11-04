package com.example.fintrack.app.budget.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.transaction.ui.TransactionAdapter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;

public final class BudgetDetailActivity extends FinanceActivity {
    private String id;
    @Override protected int layout() { return R.layout.activity_budget_detail; }
    @Override protected void bind(Bundle saved) {
        id = getIntent().getStringExtra(BudgetFormActivity.EXTRA_ID); toolbar(R.id.toolbarBudgetDetail);
        findViewById(R.id.btnEditBudget).setOnClickListener(view -> startActivity(new Intent(this, BudgetFormActivity.class).putExtra(BudgetFormActivity.EXTRA_ID, id)));
        findViewById(R.id.btnDeleteBudget).setOnClickListener(view -> confirmDelete(() -> model.change(FinanceAction.deleteBudget(id), true)));
    }
    @Override protected void render() {
        if (book == null) return;
        FinanceBook.Budget value = book.budget(id);
        if (value == null) { if (!model.busy()) finish(); return; }
        FinanceBook.BudgetUsage usage = book.usage(value);
        ((MaterialToolbar) findViewById(R.id.toolbarBudgetDetail)).setSubtitle(FinanceUi.monthLabel(value.month));
        text(R.id.tvDetailCategoryName, book.category(value.categoryId).name); text(R.id.tvDetailPercentage, usage.percentage + "%");
        text(R.id.tvDetailSpentAmount, FinanceUi.money(usage.spent)); text(R.id.tvDetailLimitAmount, getString(R.string.finance_budget_limit, FinanceUi.money(value.limit)));
        text(R.id.tvDetailStatusMessage, getString(usage.exceeded ? R.string.finance_budget_exceeded : usage.warning
                ? R.string.finance_budget_warning : R.string.finance_budget_remaining, FinanceUi.money(Math.abs(usage.remaining))));
        LinearProgressIndicator progress = findViewById(R.id.progressDetailBudget); progress.setProgress(usage.progress);
        progress.setIndicatorColor(getColor(usage.exceeded ? R.color.status_error : usage.warning ? R.color.status_warning : R.color.color_primary));
        ((RecyclerView) findViewById(R.id.rvBudgetTransactions)).setAdapter(new TransactionAdapter(book,
                book.transactions(value.month, FinanceBook.Type.EXPENSE, "", null, value.categoryId)));
        visible(R.id.tvBudgetTransactionsEmpty, book.transactions(value.month, FinanceBook.Type.EXPENSE, "", null, value.categoryId).isEmpty());
    }
    @Override protected void loading(boolean loading) {
        findViewById(R.id.btnEditBudget).setEnabled(!loading); findViewById(R.id.btnDeleteBudget).setEnabled(!loading);
    }
}
