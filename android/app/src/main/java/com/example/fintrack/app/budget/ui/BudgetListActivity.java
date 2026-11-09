package com.example.fintrack.app.budget.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fintrack.R;
import com.example.fintrack.app.budget.adapter.MonthlyBudgetAdapter;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.util.List;

/**
 * Màn hình danh sách Ngân sách tháng (BudgetListActivity): mỗi dòng là một ngân sách tháng với tổng (dẫn xuất
 * từ các danh mục), đã chi, còn lại và tiến độ. Thẻ tóm tắt hiển thị ngân sách của tháng hiện tại.
 */
public final class BudgetListActivity extends FinanceActivity {

    @Override protected com.example.fintrack.app.finance.data.FinanceRepository.Scope readScope() {
        return com.example.fintrack.app.finance.data.FinanceRepository.Scope.BUDGETS;
    }

    @Override
    protected int layout() { return R.layout.activity_budget_list; }

    @Override
    protected void bind(Bundle saved) {
        FinanceUi.navigation(this, R.id.navBudget);
        findViewById(R.id.fabAddBudget).setOnClickListener(view -> add());
        findViewById(R.id.btnStatePrimary).setOnClickListener(view -> add());
    }

    /** Chuyển sang màn hình tạo ngân sách tháng */
    private void add() { startActivity(new Intent(this, MonthlyBudgetFormActivity.class)); }

    @Override
    protected void loading(boolean loading) { visible(R.id.stateLoadingList, loading && book == null); }

    @Override
    protected void render() {
        if (book == null) return;
        String month = book.monthAt(System.currentTimeMillis());
        ((MaterialToolbar) findViewById(R.id.toolbarBudgets)).setSubtitle(FinanceUi.monthLabel(month));

        List<FinanceBook.MonthlyBudget> items = book.monthlyBudgets();
        ((RecyclerView) findViewById(R.id.rvBudgets)).setAdapter(new MonthlyBudgetAdapter(book, items));

        // Thẻ tóm tắt: ngân sách tháng hiện tại (giá trị từ server; mock tính cùng công thức)
        FinanceBook.MonthlyBudget current = book.monthlyBudgetFor(month);
        long spent = current == null ? 0 : book.monthlySpent(current), total = current == null ? 0 : book.monthlyTotal(current);
        text(R.id.tvTotalBudgetSummary, FinanceUi.money(spent) + " / " + FinanceUi.money(total));
        ((LinearProgressIndicator) findViewById(R.id.progressTotalBudget)).setProgress(current == null ? 0 : book.monthlyUsage(current).progress);

        visible(R.id.stateEmpty, items.isEmpty()); visible(R.id.rvBudgets, !items.isEmpty());
        text(R.id.tvStateTitle, getString(R.string.monthly_budget_empty)); text(R.id.tvStateBody, getString(R.string.monthly_budget_empty_body));
        text(R.id.btnStatePrimary, getString(R.string.monthly_budget_create_title));
    }
}
