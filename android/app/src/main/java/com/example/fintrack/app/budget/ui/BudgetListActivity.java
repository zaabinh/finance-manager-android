package com.example.fintrack.app.budget.ui;

import com.example.fintrack.app.finance.domain.MonthlyBudget;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import com.example.fintrack.R;
import com.example.fintrack.app.budget.adapter.MonthlyBudgetAdapter;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.databinding.ActivityBudgetListBinding;
import java.util.List;

/**
 * Màn hình danh sách Ngân sách tháng (BudgetListActivity): mỗi dòng là một ngân sách tháng với tổng (dẫn xuất
 * từ các danh mục), đã chi, còn lại và tiến độ. Thẻ tóm tắt hiển thị ngân sách của tháng hiện tại.
 */
public final class BudgetListActivity extends FinanceActivity {
    private ActivityBudgetListBinding binding;

    @Override protected com.example.fintrack.app.finance.data.FinanceRepository.Scope readScope() {
        return com.example.fintrack.app.finance.data.FinanceRepository.Scope.BUDGETS;
    }

    @Override
    protected View contentView() { binding = ActivityBudgetListBinding.inflate(getLayoutInflater()); return binding.getRoot(); }

    @Override
    protected void bind(Bundle saved) {
        FinanceUi.navigation(this, R.id.navBudget);
        binding.fabAddBudget.setOnClickListener(view -> add());
        binding.stateEmpty.btnStatePrimary.setOnClickListener(view -> add());
    }

    /** Chuyển sang màn hình tạo ngân sách tháng */
    private void add() { startActivity(new Intent(this, MonthlyBudgetFormActivity.class)); }

    @Override
    protected void loading(boolean loading) { FinanceUi.visible(binding.stateLoadingList.getRoot(), loading && book == null); }

    @Override
    protected void render() {
        if (book == null) return;
        String month = book.monthAt(System.currentTimeMillis());
        binding.toolbarBudgets.setSubtitle(FinanceUi.monthLabel(month));

        List<MonthlyBudget> items = book.monthlyBudgets();
        binding.rvBudgets.setAdapter(new MonthlyBudgetAdapter(book, items));

        // Thẻ tóm tắt: ngân sách tháng hiện tại (giá trị từ server; mock tính cùng công thức)
        MonthlyBudget current = book.monthlyBudgetFor(month);
        long spent = current == null ? 0 : book.monthlySpent(current), total = current == null ? 0 : book.monthlyTotal(current);
        binding.tvTotalBudgetSummary.setText(FinanceUi.money(spent) + " / " + FinanceUi.money(total));
        binding.progressTotalBudget.setProgress(current == null ? 0 : book.monthlyUsage(current).progress);

        FinanceUi.visible(binding.stateEmpty.getRoot(), items.isEmpty()); FinanceUi.visible(binding.rvBudgets, !items.isEmpty());
        binding.stateEmpty.tvStateTitle.setText(getString(R.string.monthly_budget_empty));
        binding.stateEmpty.tvStateBody.setText(getString(R.string.monthly_budget_empty_body));
        binding.stateEmpty.btnStatePrimary.setText(getString(R.string.monthly_budget_create_title));
    }
}
