package com.example.fintrack.app.budget.ui;

import com.example.fintrack.app.budget.domain.BudgetFormValidator;
import com.example.fintrack.app.finance.domain.Budget;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.MonthlyBudget;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.finance.ui.FinanceViewModel;
import com.example.fintrack.databinding.ActivityBudgetFormBinding;
import com.google.android.material.chip.ChipGroup;
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
    private ActivityBudgetFormBinding binding;
    // view_budget_shortfall is included without an android:id, so ViewBinding does not expose it.
    private View shortfallPanel;
    private String id, monthlyId, category, month;
    private boolean editing, initialized;

    @Override
    protected View contentView() {
        binding = ActivityBudgetFormBinding.inflate(getLayoutInflater());
        shortfallPanel = binding.getRoot().findViewById(R.id.panelShortfall);
        return binding.getRoot();
    }

    @Override
    protected void bind(Bundle saved) {
        binding.toolbarBudgetForm.setNavigationOnClickListener(view -> finish());
        com.example.fintrack.app.core.ui.MoneyInput.attach(binding.amountInputBudget.etAmount,
                binding.getRoot().<ChipGroup>findViewById(R.id.chipGroupAmountSuggestions));
        editing = getIntent().hasExtra(EXTRA_ID);
        monthlyId = getIntent().getStringExtra(EXTRA_MONTHLY_ID);
        if (monthlyId == null) {
            // A category budget cannot exist without its monthly budget: start from "Tạo ngân sách tháng".
            startActivity(new Intent(this, MonthlyBudgetFormActivity.class)); finish(); return;
        }
        id = editing ? getIntent().getStringExtra(EXTRA_ID) : UUID.randomUUID().toString();
        if (saved != null) { initialized = saved.getBoolean("initialized"); id = saved.getString("id"); category = saved.getString("category"); month = saved.getString("month"); }

        binding.toolbarBudgetForm.setTitle(editing ? R.string.budget_edit_title : R.string.budget_add_title);
        binding.amountInputBudget.tvAmountLabel.setText(getString(R.string.budget_limit_label)); binding.amountInputBudget.tvAmountSign.setVisibility(View.GONE);
        binding.btnSaveBudget.setText(getString(editing ? R.string.btn_save_changes : R.string.btn_create_budget));

        binding.fieldBudgetCategory.setOnClickListener(view -> BudgetUi.pickExpenseCategory(this, book, usedCategories(),
                value -> { category = value; BudgetUi.hideShortfall(shortfallPanel); render(); }));
        // The month belongs to the parent monthly budget and is not editable here.
        binding.fieldBudgetPeriod.setClickable(false);
        binding.btnSaveBudget.setOnClickListener(view -> save());
    }

    /** Categories already budgeted in this month, except the one being edited. */
    private Set<String> usedCategories() {
        Set<String> used = new HashSet<>();
        MonthlyBudget monthly = book == null ? null : book.monthlyBudget(monthlyId);
        if (monthly != null) for (Budget item : book.categoryBudgets(monthly)) if (!item.id.equals(id)) used.add(item.categoryId);
        return used;
    }

    @Override
    protected void render() {
        if (book == null) return;
        MonthlyBudget monthly = book.monthlyBudget(monthlyId);
        if (monthly == null || monthly.archivedAt != null) {
            if (!model.busy()) { Toast.makeText(this, R.string.finance_not_found, Toast.LENGTH_LONG).show(); finish(); }
            return;
        }
        if (!initialized) {
            month = monthly.month;
            if (editing) {
                Budget value = book.budget(id);
                if (value == null || value.archivedAt != null) { Toast.makeText(this, R.string.finance_not_found, Toast.LENGTH_LONG).show(); finish(); return; }
                category = value.categoryId; binding.amountInputBudget.etAmount.setText(Long.toString(value.limit));
            }
            initialized = true;
        }
        binding.tvBudgetCatValue.setText(book.category(category) == null ? getString(R.string.transaction_category_placeholder) : book.category(category).name);
        binding.tvBudgetPeriodValue.setText(FinanceUi.monthLabel(month));
    }

    /** Xử lý kiểm tra dữ liệu và lưu ngân sách danh mục */
    private void save() {
        if (book == null || model.busy() || month == null) return;

        BudgetFormValidator.Result result = BudgetFormValidator.category(id, monthlyId, category, month, usedCategories(),
                binding.amountInputBudget.etAmount.getText().toString());
        if (!result.valid()) {
            switch (result.error) {
                case MISSING_CATEGORY: Toast.makeText(this, R.string.error_required_category, Toast.LENGTH_SHORT).show(); break;
                case DUPLICATE_CATEGORY: Toast.makeText(this, R.string.error_duplicate_budget, Toast.LENGTH_SHORT).show(); break;
                default:
                    binding.amountInputBudget.tvAmountError.setText(getString(R.string.finance_amount_error));
                    binding.amountInputBudget.tvAmountError.setVisibility(View.VISIBLE);
                    binding.amountInputBudget.etAmount.requestFocus();
            }
            return;
        }
        binding.amountInputBudget.tvAmountError.setVisibility(View.GONE);
        BudgetUi.hideShortfall(shortfallPanel);
        model.change(FinanceAction.budget(result.budget, editing), true);
    }

    @Override
    protected CharSequence failure(FinanceViewModel.Command command) {
        CharSequence text = BudgetUi.failure(this, book, command, shortfallPanel);
        boolean shortfall = command.failure != null && FinanceBook.BUDGET_EXCEEDS_AVAILABLE_BALANCE.equals(command.failure.code);
        return text != null || shortfall ? text : super.failure(command);
    }

    @Override
    protected void loading(boolean loading) { FinanceUi.progress(binding.btnSaveBudget, loading); }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putString("id", id); out.putString("category", category); out.putString("month", month); out.putBoolean("initialized", initialized);
    }
}
