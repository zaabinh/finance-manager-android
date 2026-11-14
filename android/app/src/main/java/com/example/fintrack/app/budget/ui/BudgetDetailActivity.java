package com.example.fintrack.app.budget.ui;

import com.example.fintrack.app.finance.domain.Budget;
import com.example.fintrack.app.finance.domain.BudgetUsage;
import com.example.fintrack.app.finance.domain.MonthlyBudget;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import com.example.fintrack.R;
import com.example.fintrack.app.budget.adapter.BudgetAdapter;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.finance.ui.FinanceViewModel;
import com.example.fintrack.app.transaction.ui.TransactionListActivity;
import com.example.fintrack.databinding.ActivityBudgetDetailBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.List;

/**
 * Chi tiết Ngân sách tháng: tổng (dẫn xuất) / đã chi / còn lại và các ngân sách danh mục với hạn mức, đã chi,
 * còn lại, tiến độ. Thêm, sửa, xóa ngân sách danh mục; xóa ngân sách tháng (ẩn với tháng hiện tại).
 */
public final class BudgetDetailActivity extends FinanceActivity {
    public static final String EXTRA_MONTHLY_ID = "monthly_budget_id";

    private ActivityBudgetDetailBinding binding;
    private String id;

    @Override protected com.example.fintrack.app.finance.data.FinanceRepository.Scope readScope() {
        return com.example.fintrack.app.finance.data.FinanceRepository.Scope.BUDGETS;
    }

    @Override protected String detailId() { return getIntent().getStringExtra(EXTRA_MONTHLY_ID); }

    @Override
    protected View contentView() { binding = ActivityBudgetDetailBinding.inflate(getLayoutInflater()); return binding.getRoot(); }

    @Override
    protected void bind(Bundle saved) {
        id = getIntent().getStringExtra(EXTRA_MONTHLY_ID); binding.toolbarBudgetDetail.setNavigationOnClickListener(view -> finish());
        binding.btnAddCategoryBudget.setOnClickListener(view -> startActivity(new Intent(this, BudgetFormActivity.class)
                .putExtra(BudgetFormActivity.EXTRA_MONTHLY_ID, id)));
        binding.btnDeleteBudget.setOnClickListener(view -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.monthly_budget_delete_title).setMessage(R.string.monthly_budget_delete_warning)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_delete, (dialog, which) -> model.change(FinanceAction.deleteMonthlyBudget(id), true)).show());
        binding.btnBudgetHistory.setOnClickListener(view -> {
            MonthlyBudget monthly = book == null ? null : book.monthlyBudget(id);
            if (monthly != null) startActivity(new Intent(this, TransactionListActivity.class).putExtra("month", monthly.month));
        });
    }

    @Override
    protected void render() {
        if (book == null) return;
        MonthlyBudget monthly = book.monthlyBudget(id);
        if (monthly == null) { if (!model.busy()) finish(); return; }
        BudgetUsage usage = book.monthlyUsage(monthly);
        long total = book.monthlyTotal(monthly);
        boolean active = monthly.archivedAt == null;

        binding.toolbarBudgetDetail.setSubtitle(FinanceUi.monthLabel(monthly.month));
        binding.tvDetailCategoryName.setText(FinanceUi.monthLabel(monthly.month));
        binding.tvDetailPercentage.setText(usage.percentage + "%");
        binding.tvDetailSpentAmount.setText(FinanceUi.money(usage.spent));
        binding.tvDetailLimitAmount.setText(getString(R.string.monthly_budget_total, FinanceUi.money(total)));
        binding.tvDetailStatusMessage.setText(getString(usage.exceeded ? R.string.finance_budget_exceeded : usage.warning
                ? R.string.finance_budget_warning : R.string.finance_budget_remaining, FinanceUi.money(Math.abs(usage.remaining))));
        binding.progressDetailBudget.setProgress(usage.progress);
        binding.progressDetailBudget.setIndicatorColor(getColor(usage.exceeded ? R.color.status_error : usage.warning ? R.color.status_warning : R.color.color_primary));

        List<Budget> children = book.categoryBudgets(monthly);
        binding.rvCategoryBudgets.setAdapter(new BudgetAdapter(book, children,
                active ? this::categoryActions : null));
        FinanceUi.visible(binding.rvCategoryBudgets, !children.isEmpty());
        FinanceUi.visible(binding.tvCategoryBudgetsEmpty, children.isEmpty());
        FinanceUi.visible(binding.btnAddCategoryBudget, active);
        // The current month (user timezone) cannot be deleted: hide the action instead of failing later.
        FinanceUi.visible(binding.btnDeleteBudget, active && !monthly.month.equals(book.monthAt(System.currentTimeMillis())));
    }

    /** Sửa hạn mức hoặc xóa một ngân sách danh mục khỏi ngân sách tháng. */
    private void categoryActions(Budget budget) {
        CharSequence[] actions = {getString(R.string.budget_category_edit_limit), getString(R.string.budget_category_remove)};
        new MaterialAlertDialogBuilder(this).setItems(actions, (dialog, which) -> {
            if (which == 0) startActivity(new Intent(this, BudgetFormActivity.class)
                    .putExtra(BudgetFormActivity.EXTRA_MONTHLY_ID, id).putExtra(BudgetFormActivity.EXTRA_ID, budget.id));
            else new MaterialAlertDialogBuilder(this).setTitle(R.string.budget_category_remove_title)
                    .setMessage(R.string.budget_category_remove_body).setNegativeButton(R.string.btn_cancel, null)
                    .setPositiveButton(R.string.btn_delete, (d, w) -> model.change(FinanceAction.deleteBudget(budget.id), false)).show();
        }).show();
    }

    @Override
    protected CharSequence failure(FinanceViewModel.Command command) {
        CharSequence text = BudgetUi.failure(this, book, command, null);
        return text != null ? text : super.failure(command);
    }

    @Override
    protected void loading(boolean loading) {
        binding.btnAddCategoryBudget.setEnabled(!loading); binding.btnDeleteBudget.setEnabled(!loading);
    }
}
