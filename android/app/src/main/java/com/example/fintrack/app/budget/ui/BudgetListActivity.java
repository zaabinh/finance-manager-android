package com.example.fintrack.app.budget.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;

public final class BudgetListActivity extends FinanceActivity {
    private String month = FinanceBook.month(System.currentTimeMillis());
    @Override protected int layout() { return R.layout.activity_budget_list; }
    @Override protected void bind(Bundle saved) {
        if (saved != null) month = saved.getString("month", month);
        FinanceUi.navigation(this, R.id.navTabKhoan);
        ((MaterialToolbar) findViewById(R.id.toolbarBudgets)).getMenu().add(R.string.transaction_change_month)
                .setOnMenuItemClickListener(item -> { FinanceUi.month(this, month, value -> { month = value; render(); }); return true; });
        findViewById(R.id.fabAddBudget).setOnClickListener(view -> add());
        findViewById(R.id.btnStatePrimary).setOnClickListener(view -> add());
    }
    private void add() { startActivity(new Intent(this, BudgetFormActivity.class).putExtra("month", month)); }
    @Override protected void loading(boolean loading) { visible(R.id.stateLoadingList, loading && book == null); }
    @Override protected void render() {
        if (book == null) return;
        ((MaterialToolbar) findViewById(R.id.toolbarBudgets)).setSubtitle(FinanceUi.monthLabel(month));
        ((RecyclerView) findViewById(R.id.rvBudgets)).setAdapter(new BudgetAdapter(book, book.budgets(month)));
        long spent = book.budgetSpent(month), limit = book.budgetLimit(month);
        text(R.id.tvTotalBudgetSummary, FinanceUi.money(spent) + " / " + FinanceUi.money(limit));
        ((LinearProgressIndicator) findViewById(R.id.progressTotalBudget)).setProgress(limit == 0 ? 0
                : java.math.BigInteger.valueOf(spent).multiply(java.math.BigInteger.valueOf(100))
                .divide(java.math.BigInteger.valueOf(limit)).min(java.math.BigInteger.valueOf(100)).intValue());
        visible(R.id.stateEmpty, book.budgets(month).isEmpty()); visible(R.id.rvBudgets, !book.budgets(month).isEmpty());
        text(R.id.tvStateTitle, getString(R.string.finance_budget_empty)); text(R.id.tvStateBody, getString(R.string.finance_budget_empty_body));
        text(R.id.btnStatePrimary, getString(R.string.btn_create_budget));
    }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString("month", month); }
}
