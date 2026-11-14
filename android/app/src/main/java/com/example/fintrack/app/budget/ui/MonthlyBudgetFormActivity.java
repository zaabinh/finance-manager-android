package com.example.fintrack.app.budget.ui;

import com.example.fintrack.app.budget.domain.BudgetFormValidator;
import com.example.fintrack.app.finance.domain.Budget;
import com.example.fintrack.app.finance.domain.Category;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.FinanceFailure;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.finance.ui.FinanceViewModel;
import com.example.fintrack.databinding.ActivityMonthlyBudgetFormBinding;
import com.example.fintrack.databinding.ItemMonthlyBudgetRowBinding;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;

/**
 * "Tạo ngân sách tháng": chọn tháng và các dòng danh mục (mỗi danh mục chi tiêu đang hoạt động tối đa một lần).
 * Tổng tháng chỉ hiển thị = tổng các dòng; không nhập trực tiếp. Lưu một request nguyên tử.
 */
public final class MonthlyBudgetFormActivity extends FinanceActivity {
    private static final String KEY_MONTH = "month", KEY_CATEGORIES = "categories", KEY_LIMITS = "limits", KEY_ID = "id";

    @Override protected com.example.fintrack.app.finance.data.FinanceRepository.Scope readScope() {
        return com.example.fintrack.app.finance.data.FinanceRepository.Scope.BUDGETS;
    }

    private ActivityMonthlyBudgetFormBinding binding;
    // view_budget_shortfall is included without an android:id, so ViewBinding does not expose it.
    private View shortfallPanel;
    private String id, month;
    private final ArrayList<String> categories = new ArrayList<>();
    private final ArrayList<String> limits = new ArrayList<>();
    private boolean monthChosen;

    @Override protected View contentView() {
        binding = ActivityMonthlyBudgetFormBinding.inflate(getLayoutInflater());
        shortfallPanel = binding.getRoot().findViewById(R.id.panelShortfall);
        return binding.getRoot();
    }

    @Override
    protected void bind(Bundle saved) {
        binding.toolbarMonthlyBudgetForm.setNavigationOnClickListener(view -> finish());
        id = UUID.randomUUID().toString();
        if (getIntent().hasExtra("month")) { month = getIntent().getStringExtra("month"); monthChosen = true; }
        if (saved != null) {
            id = saved.getString(KEY_ID, id);
            month = saved.getString(KEY_MONTH); monthChosen = month != null;
            ArrayList<String> c = saved.getStringArrayList(KEY_CATEGORIES), l = saved.getStringArrayList(KEY_LIMITS);
            if (c != null && l != null && c.size() == l.size()) { categories.addAll(c); limits.addAll(l); }
        }
        binding.fieldMonthlyBudgetPeriod.setOnClickListener(view -> FinanceUi.month(this, currentMonth(),
                value -> { month = value; monthChosen = true; BudgetUi.hideShortfall(shortfallPanel); render(); }));
        binding.btnAddBudgetRow.setOnClickListener(view -> BudgetUi.pickExpenseCategory(this, book, new HashSet<>(categories),
                category -> { categories.add(category); limits.add(""); rebuildRows(); }));
        binding.btnSaveMonthlyBudget.setOnClickListener(view -> save());
        rebuildRows();
    }

    private String currentMonth() {
        if (month != null) return month;
        return book == null ? FinanceBook.month(System.currentTimeMillis()) : book.monthAt(System.currentTimeMillis());
    }

    @Override
    protected void render() {
        if (book == null) return;
        if (!monthChosen) month = book.monthAt(System.currentTimeMillis());
        binding.tvMonthlyBudgetPeriodValue.setText(FinanceUi.monthLabel(currentMonth()));
        // Only refresh category names; rebuilding would drop focus while the user types.
        for (int i = 0; i < binding.containerBudgetRows.getChildCount() && i < categories.size(); i++) {
            Category category = book.category(categories.get(i));
            ItemMonthlyBudgetRowBinding row = ItemMonthlyBudgetRowBinding.bind(binding.containerBudgetRows.getChildAt(i));
            row.tvRowCategory.setText(category == null ? getString(R.string.transaction_category_placeholder) : category.name);
        }
    }

    /** Rows are rebuilt from the saved lists so input survives rotation and book refreshes. */
    private void rebuildRows() {
        binding.containerBudgetRows.removeAllViews();
        for (int i = 0; i < categories.size(); i++) {
            final int index = i;
            ItemMonthlyBudgetRowBinding row = ItemMonthlyBudgetRowBinding.inflate(getLayoutInflater(), binding.containerBudgetRows, false);
            Category category = book == null ? null : book.category(categories.get(i));
            row.tvRowCategory.setText(category == null ? getString(R.string.transaction_category_placeholder) : category.name);
            com.example.fintrack.app.core.ui.MoneyInput.attach(row.etRowLimit);
            row.etRowLimit.setText(limits.get(i));
            row.etRowLimit.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
                @Override public void afterTextChanged(Editable s) {
                    if (index < limits.size()) { limits.set(index, s.toString()); BudgetUi.hideShortfall(shortfallPanel); updateTotal(); }
                }
            });
            row.btnRemoveRow.setOnClickListener(view -> {
                categories.remove(index); limits.remove(index); BudgetUi.hideShortfall(shortfallPanel); rebuildRows();
            });
            binding.containerBudgetRows.addView(row.getRoot());
        }
        updateTotal();
    }

    /** Live total = sum of the valid row limits; it is only displayed, never sent. */
    private void updateTotal() {
        long total = 0;
        for (String value : limits) {
            try { total = Math.addExact(total, FinanceBook.parseAmount(value.trim())); }
            catch (FinanceFailure | ArithmeticException ignored) { }
        }
        binding.tvMonthlyBudgetTotal.setText(getString(R.string.monthly_budget_live_total, FinanceUi.money(total)));
    }

    private void save() {
        if (book == null || model.busy()) return;
        BudgetFormValidator.Result result = BudgetFormValidator.monthly(currentMonth(), book.monthlyBudgetFor(currentMonth()) != null,
                categories, limits, () -> UUID.randomUUID().toString());
        if (!result.valid()) {
            switch (result.error) {
                case NO_CATEGORIES: Toast.makeText(this, R.string.monthly_budget_category_required, Toast.LENGTH_SHORT).show(); break;
                case MONTH_EXISTS: Toast.makeText(this, R.string.monthly_budget_exists, Toast.LENGTH_SHORT).show(); break;
                default:
                    Toast.makeText(this, R.string.finance_amount_error, Toast.LENGTH_SHORT).show();
                    ItemMonthlyBudgetRowBinding.bind(binding.containerBudgetRows.getChildAt(result.row)).etRowLimit.requestFocus();
            }
            return;
        }
        BudgetUi.hideShortfall(shortfallPanel);
        model.change(FinanceAction.monthlyBudget(id, currentMonth(), result.lines), true);
    }

    @Override
    protected CharSequence failure(FinanceViewModel.Command command) {
        String code = command.failure == null ? null : command.failure.code;
        if (FinanceBook.BUDGET_PERIOD_CONFLICT.equals(code)) return getString(R.string.monthly_budget_exists);
        CharSequence text = BudgetUi.failure(this, book, command, shortfallPanel);
        // Shortfall is shown inline (text null); the form keeps all input.
        return text != null || FinanceBook.BUDGET_EXCEEDS_AVAILABLE_BALANCE.equals(code) ? text : super.failure(command);
    }

    @Override
    protected void loading(boolean loading) { FinanceUi.progress(binding.btnSaveMonthlyBudget, loading); }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(KEY_ID, id); out.putString(KEY_MONTH, monthChosen ? month : null);
        out.putStringArrayList(KEY_CATEGORIES, categories); out.putStringArrayList(KEY_LIMITS, limits);
    }
}
