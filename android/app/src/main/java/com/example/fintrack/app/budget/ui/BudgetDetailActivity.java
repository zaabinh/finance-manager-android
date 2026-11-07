package com.example.fintrack.app.budget.ui;

import android.content.Intent;
import android.os.Bundle;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.transaction.ui.TransactionListActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;

/**
 * Màn hình Chi tiết Ngân sách (BudgetDetailActivity):
 * Hiển thị chi tiết khoản ngân sách, số tiền đã chi, số tiền còn lại,
 * cờ cảnh báo rủi ro và danh sách tất cả các giao dịch liên quan trong tháng.
 */
public final class BudgetDetailActivity extends FinanceActivity {

    @Override protected com.example.fintrack.app.finance.data.FinanceRepository.Scope readScope() {
        return com.example.fintrack.app.finance.data.FinanceRepository.Scope.BUDGETS;
    }

    @Override protected String detailId() { return getIntent().getStringExtra(BudgetFormActivity.EXTRA_ID); }

    private String id;

    @Override 
    protected int layout() { return R.layout.activity_budget_detail; }

    @Override 
    protected void bind(Bundle saved) {
        id = getIntent().getStringExtra(BudgetFormActivity.EXTRA_ID); toolbar(R.id.toolbarBudgetDetail);
        
        // Sự kiện bấm nút Sửa hoặc Xóa ngân sách
        findViewById(R.id.btnEditBudget).setOnClickListener(view -> startActivity(new Intent(this, BudgetFormActivity.class).putExtra(BudgetFormActivity.EXTRA_ID, id)));
        findViewById(R.id.btnDeleteBudget).setOnClickListener(view -> confirmDelete(() -> model.change(FinanceAction.deleteBudget(id), true)));
        findViewById(R.id.btnBudgetHistory).setOnClickListener(view -> {
            FinanceBook.Budget budget = book == null ? null : book.budget(id);
            if (budget != null) startActivity(new Intent(this, TransactionListActivity.class)
                    .putExtra("category_id", budget.categoryId).putExtra("month", budget.month));
        });
    }

    @Override 
    protected void render() {
        if (book == null) return;
        FinanceBook.Budget value = book.budget(id);
        if (value == null) { if (!model.busy()) finish(); return; }
        
        // Tính toán mức độ sử dụng ngân sách từ FinanceBook
        FinanceBook.BudgetUsage usage = book.usage(value);
        
        ((MaterialToolbar) findViewById(R.id.toolbarBudgetDetail)).setSubtitle(FinanceUi.monthLabel(value.month));
        text(R.id.tvDetailCategoryName, book.category(value.categoryId) == null ? getString(R.string.transaction_category_placeholder) : book.category(value.categoryId).name); text(R.id.tvDetailPercentage, usage.percentage + "%");
        text(R.id.tvDetailSpentAmount, FinanceUi.money(usage.spent)); text(R.id.tvDetailLimitAmount, getString(R.string.finance_budget_limit, FinanceUi.money(value.limit)));
        
        // Hiển thị thông điệp trạng thái (Vượt hạn mức / Cảnh báo / Còn lại)
        text(R.id.tvDetailStatusMessage, getString(usage.exceeded ? R.string.finance_budget_exceeded : usage.warning
                ? R.string.finance_budget_warning : R.string.finance_budget_remaining, FinanceUi.money(Math.abs(usage.remaining))));
        
        // Cập nhật thanh tiến độ và màu sắc chỉ báo
        LinearProgressIndicator progress = findViewById(R.id.progressDetailBudget); progress.setProgress(usage.progress);
        progress.setIndicatorColor(getColor(usage.exceeded ? R.color.status_error : usage.warning ? R.color.status_warning : R.color.color_primary));
        
        // Hiển thị danh sách các giao dịch thuộc ngân sách này trong tháng
        visible(R.id.btnEditBudget, value.archivedAt == null);
        visible(R.id.btnDeleteBudget, value.archivedAt == null);
        // Budget reads only references and authoritative server usage, never a partial ledger.
        visible(R.id.rvBudgetTransactions, false);
        visible(R.id.tvBudgetTransactionsEmpty, true);
        text(R.id.tvBudgetTransactionsEmpty, getString(R.string.budget_history_hint));
    }

    @Override 
    protected void loading(boolean loading) {
        findViewById(R.id.btnEditBudget).setEnabled(!loading); findViewById(R.id.btnDeleteBudget).setEnabled(!loading);
    }
}
