package com.example.fintrack.app.budget.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fintrack.R;
import com.example.fintrack.app.budget.adapter.BudgetAdapter;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.finance.ui.FinanceViewModel;
import com.example.fintrack.app.transaction.ui.TransactionListActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.util.List;

/**
 * Chi tiết Ngân sách tháng: tổng (dẫn xuất) / đã chi / còn lại và các ngân sách danh mục với hạn mức, đã chi,
 * còn lại, tiến độ. Thêm, sửa, xóa ngân sách danh mục; xóa ngân sách tháng (ẩn với tháng hiện tại).
 */
public final class BudgetDetailActivity extends FinanceActivity {
    public static final String EXTRA_MONTHLY_ID = "monthly_budget_id";

    private String id;

    @Override protected com.example.fintrack.app.finance.data.FinanceRepository.Scope readScope() {
        return com.example.fintrack.app.finance.data.FinanceRepository.Scope.BUDGETS;
    }

    @Override protected String detailId() { return getIntent().getStringExtra(EXTRA_MONTHLY_ID); }

    @Override
    protected int layout() { return R.layout.activity_budget_detail; }

    @Override
    protected void bind(Bundle saved) {
        id = getIntent().getStringExtra(EXTRA_MONTHLY_ID); toolbar(R.id.toolbarBudgetDetail);
        findViewById(R.id.btnAddCategoryBudget).setOnClickListener(view -> startActivity(new Intent(this, BudgetFormActivity.class)
                .putExtra(BudgetFormActivity.EXTRA_MONTHLY_ID, id)));
        findViewById(R.id.btnDeleteBudget).setOnClickListener(view -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.monthly_budget_delete_title).setMessage(R.string.monthly_budget_delete_warning)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_delete, (dialog, which) -> model.change(FinanceAction.deleteMonthlyBudget(id), true)).show());
        findViewById(R.id.btnBudgetHistory).setOnClickListener(view -> {
            FinanceBook.MonthlyBudget monthly = book == null ? null : book.monthlyBudget(id);
            if (monthly != null) startActivity(new Intent(this, TransactionListActivity.class).putExtra("month", monthly.month));
        });
    }

    @Override
    protected void render() {
        if (book == null) return;
        FinanceBook.MonthlyBudget monthly = book.monthlyBudget(id);
        if (monthly == null) { if (!model.busy()) finish(); return; }
        FinanceBook.BudgetUsage usage = book.monthlyUsage(monthly);
        long total = book.monthlyTotal(monthly);
        boolean active = monthly.archivedAt == null;

        ((MaterialToolbar) findViewById(R.id.toolbarBudgetDetail)).setSubtitle(FinanceUi.monthLabel(monthly.month));
        text(R.id.tvDetailCategoryName, FinanceUi.monthLabel(monthly.month));
        text(R.id.tvDetailPercentage, usage.percentage + "%");
        text(R.id.tvDetailSpentAmount, FinanceUi.money(usage.spent));
        text(R.id.tvDetailLimitAmount, getString(R.string.monthly_budget_total, FinanceUi.money(total)));
        text(R.id.tvDetailStatusMessage, getString(usage.exceeded ? R.string.finance_budget_exceeded : usage.warning
                ? R.string.finance_budget_warning : R.string.finance_budget_remaining, FinanceUi.money(Math.abs(usage.remaining))));
        LinearProgressIndicator progress = findViewById(R.id.progressDetailBudget); progress.setProgress(usage.progress);
        progress.setIndicatorColor(getColor(usage.exceeded ? R.color.status_error : usage.warning ? R.color.status_warning : R.color.color_primary));

        List<FinanceBook.Budget> children = book.categoryBudgets(monthly);
        ((RecyclerView) findViewById(R.id.rvCategoryBudgets)).setAdapter(new BudgetAdapter(book, children,
                active ? this::categoryActions : null));
        visible(R.id.rvCategoryBudgets, !children.isEmpty());
        visible(R.id.tvCategoryBudgetsEmpty, children.isEmpty());
        visible(R.id.btnAddCategoryBudget, active);
        // The current month (user timezone) cannot be deleted: hide the action instead of failing later.
        visible(R.id.btnDeleteBudget, active && !monthly.month.equals(book.monthAt(System.currentTimeMillis())));
    }

    /** Sửa hạn mức hoặc xóa một ngân sách danh mục khỏi ngân sách tháng. */
    private void categoryActions(FinanceBook.Budget budget) {
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
        CharSequence text = BudgetUi.failure(this, book, command);
        return text != null ? text : super.failure(command);
    }

    @Override
    protected void loading(boolean loading) {
        findViewById(R.id.btnAddCategoryBudget).setEnabled(!loading); findViewById(R.id.btnDeleteBudget).setEnabled(!loading);
    }
}
