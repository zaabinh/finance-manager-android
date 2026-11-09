package com.example.fintrack.app.budget.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.finance.ui.FinanceViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Form Ngân sách danh mục: thêm hoặc sửa hạn mức của một danh mục trong một ngân sách tháng.
 * Tháng cố định theo ngân sách tháng cha; mỗi danh mục chỉ xuất hiện một lần trong tháng.
 */
public final class BudgetFormActivity extends FinanceActivity {

    @Override protected com.example.fintrack.app.finance.data.FinanceRepository.Scope readScope() {
        return com.example.fintrack.app.finance.data.FinanceRepository.Scope.BUDGETS;
    }

    /** The server snapshot is the parent monthly budget with its category budgets. */
    @Override protected String detailId() { return getIntent().getStringExtra(EXTRA_MONTHLY_ID); }

    public static final String EXTRA_ID = "budget_id";
    public static final String EXTRA_MONTHLY_ID = "monthly_budget_id";
    private String id, monthlyId, category, month;
    private boolean editing, initialized;

    @Override
    protected int layout() { return R.layout.activity_budget_form; }

    @Override
    protected void bind(Bundle saved) {
        toolbar(R.id.toolbarBudgetForm); editing = getIntent().hasExtra(EXTRA_ID);
        monthlyId = getIntent().getStringExtra(EXTRA_MONTHLY_ID);
        if (monthlyId == null) {
            // A category budget cannot exist without its monthly budget: start from "Tạo ngân sách tháng".
            startActivity(new Intent(this, MonthlyBudgetFormActivity.class)); finish(); return;
        }
        id = editing ? getIntent().getStringExtra(EXTRA_ID) : UUID.randomUUID().toString();
        if (saved != null) { initialized = saved.getBoolean("initialized"); id = saved.getString("id"); category = saved.getString("category"); month = saved.getString("month"); }

        ((MaterialToolbar) findViewById(R.id.toolbarBudgetForm)).setTitle(editing ? R.string.budget_edit_title : R.string.budget_add_title);
        text(R.id.tvAmountLabel, getString(R.string.budget_limit_label)); visible(R.id.tvAmountSign, false);
        text(R.id.btnSaveBudget, getString(editing ? R.string.btn_save_changes : R.string.btn_create_budget));

        findViewById(R.id.fieldBudgetCategory).setOnClickListener(view -> BudgetUi.pickExpenseCategory(this, book, usedCategories(),
                value -> { category = value; BudgetUi.hideShortfall(this); render(); }));
        // The month belongs to the parent monthly budget and is not editable here.
        findViewById(R.id.fieldBudgetPeriod).setClickable(false);
        findViewById(R.id.btnSaveBudget).setOnClickListener(view -> save());
    }

    /** Categories already budgeted in this month, except the one being edited. */
    private Set<String> usedCategories() {
        Set<String> used = new HashSet<>();
        FinanceBook.MonthlyBudget monthly = book == null ? null : book.monthlyBudget(monthlyId);
        if (monthly != null) for (FinanceBook.Budget item : book.categoryBudgets(monthly)) if (!item.id.equals(id)) used.add(item.categoryId);
        return used;
    }

    @Override
    protected void render() {
        if (book == null) return;
        FinanceBook.MonthlyBudget monthly = book.monthlyBudget(monthlyId);
        if (monthly == null || monthly.archivedAt != null) {
            if (!model.busy()) { Toast.makeText(this, R.string.finance_not_found, Toast.LENGTH_LONG).show(); finish(); }
            return;
        }
        if (!initialized) {
            month = monthly.month;
            if (editing) {
                FinanceBook.Budget value = book.budget(id);
                if (value == null || value.archivedAt != null) { Toast.makeText(this, R.string.finance_not_found, Toast.LENGTH_LONG).show(); finish(); return; }
                category = value.categoryId; text(R.id.etAmount, Long.toString(value.limit));
            }
            initialized = true;
        }
        text(R.id.tvBudgetCatValue, book.category(category) == null ? getString(R.string.transaction_category_placeholder) : book.category(category).name);
        text(R.id.tvBudgetPeriodValue, FinanceUi.monthLabel(month));
    }

    /** Xử lý kiểm tra dữ liệu và lưu ngân sách danh mục */
    private void save() {
        if (book == null || model.busy() || month == null) return;

        if (category == null || category.trim().isEmpty()) {
            Toast.makeText(this, R.string.error_required_category, Toast.LENGTH_SHORT).show();
            return;
        }
        if (usedCategories().contains(category)) {
            Toast.makeText(this, R.string.error_duplicate_budget, Toast.LENGTH_SHORT).show();
            return;
        }

        long limit;
        try { limit = FinanceBook.parseAmount(((EditText) findViewById(R.id.etAmount)).getText().toString()); }
        catch (FinanceBook.Failure error) {
            text(R.id.tvAmountError, getString(R.string.finance_amount_error)); visible(R.id.tvAmountError, true);
            findViewById(R.id.etAmount).requestFocus(); return;
        }
        visible(R.id.tvAmountError, false);
        BudgetUi.hideShortfall(this);

        FinanceBook.Budget value = new FinanceBook.Budget(id, category, month, limit);
        value.monthlyBudgetId = monthlyId;
        model.change(FinanceAction.budget(value, editing), true);
    }

    @Override
    protected CharSequence failure(FinanceViewModel.Command command) {
        CharSequence text = BudgetUi.failure(this, book, command);
        boolean shortfall = command.failure != null && FinanceBook.BUDGET_EXCEEDS_AVAILABLE_BALANCE.equals(command.failure.code);
        return text != null || shortfall ? text : super.failure(command);
    }

    @Override
    protected void loading(boolean loading) { findViewById(R.id.btnSaveBudget).setEnabled(!loading); }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putString("id", id); out.putString("category", category); out.putString("month", month); out.putBoolean("initialized", initialized);
    }
}
