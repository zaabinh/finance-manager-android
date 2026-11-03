package com.example.fintrack.app.budget.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fintrack.R;
import com.example.fintrack.app.budget.adapter.BudgetAdapter;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.progressindicator.LinearProgressIndicator;

/**
 * Màn hình danh sách Ngân sách (BudgetListActivity):
 * Hiển thị tất cả các khoản ngân sách theo tháng được chọn kèm thanh tiến độ chi tiêu.
 */
public final class BudgetListActivity extends FinanceActivity {

    private String month = FinanceBook.month(System.currentTimeMillis());

    @Override 
    protected int layout() { return R.layout.activity_budget_list; }

    @Override 
    protected void bind(Bundle saved) {
        if (saved != null) month = saved.getString("month", month);
        FinanceUi.navigation(this, R.id.navBudget);
        
        // Tạo menu đổi tháng báo cáo ngân sách
        ((MaterialToolbar) findViewById(R.id.toolbarBudgets)).getMenu().add(R.string.transaction_change_month)
                .setOnMenuItemClickListener(item -> { FinanceUi.month(this, month, value -> { month = value; render(); }); return true; });
        
        findViewById(R.id.fabAddBudget).setOnClickListener(view -> add());
        findViewById(R.id.btnStatePrimary).setOnClickListener(view -> add());
    }

    /** Chuyển sang màn hình tạo ngân sách mới */
    private void add() { startActivity(new Intent(this, BudgetFormActivity.class).putExtra("month", month)); }

    @Override 
    protected void loading(boolean loading) { visible(R.id.stateLoadingList, loading && book == null); }

    @Override 
    protected void render() {
        if (book == null) return;
        ((MaterialToolbar) findViewById(R.id.toolbarBudgets)).setSubtitle(FinanceUi.monthLabel(month));
        
        // Cập nhật danh sách ngân sách lên RecyclerView qua BudgetAdapter
        ((RecyclerView) findViewById(R.id.rvBudgets)).setAdapter(new BudgetAdapter(book, book.budgets(month)));
        
        // Tính tổng tiền đã chi tiêu và tổng hạn mức ngân sách trong tháng
        long spent = book.budgetSpent(month), limit = book.budgetLimit(month);
        text(R.id.tvTotalBudgetSummary, FinanceUi.money(spent) + " / " + FinanceUi.money(limit));
        
        // Tính phần trăm tổng tiến độ ngân sách tháng
        ((LinearProgressIndicator) findViewById(R.id.progressTotalBudget)).setProgress(limit == 0 ? 0
                : java.math.BigInteger.valueOf(spent).multiply(java.math.BigInteger.valueOf(100))
                .divide(java.math.BigInteger.valueOf(limit)).min(java.math.BigInteger.valueOf(100)).intValue());
        
        // Hiển thị trạng thái danh sách trống nếu chưa thiết lập ngân sách nào
        visible(R.id.stateEmpty, book.budgets(month).isEmpty()); visible(R.id.rvBudgets, !book.budgets(month).isEmpty());
        text(R.id.tvStateTitle, getString(R.string.finance_budget_empty)); text(R.id.tvStateBody, getString(R.string.finance_budget_empty_body));
        text(R.id.btnStatePrimary, getString(R.string.btn_create_budget));
    }

    @Override 
    protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putString("month", month); }
}
