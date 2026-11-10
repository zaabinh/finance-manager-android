package com.example.fintrack.app.budget.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.finance.ui.FinanceViewModel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    private String id, month;
    private final ArrayList<String> categories = new ArrayList<>();
    private final ArrayList<String> limits = new ArrayList<>();
    private boolean monthChosen;

    @Override protected int layout() { return R.layout.activity_monthly_budget_form; }

    @Override
    protected void bind(Bundle saved) {
        toolbar(R.id.toolbarMonthlyBudgetForm);
        id = UUID.randomUUID().toString();
        if (getIntent().hasExtra("month")) { month = getIntent().getStringExtra("month"); monthChosen = true; }
        if (saved != null) {
            id = saved.getString(KEY_ID, id);
            month = saved.getString(KEY_MONTH); monthChosen = month != null;
            ArrayList<String> c = saved.getStringArrayList(KEY_CATEGORIES), l = saved.getStringArrayList(KEY_LIMITS);
            if (c != null && l != null && c.size() == l.size()) { categories.addAll(c); limits.addAll(l); }
        }
        findViewById(R.id.fieldMonthlyBudgetPeriod).setOnClickListener(view -> FinanceUi.month(this, currentMonth(),
                value -> { month = value; monthChosen = true; BudgetUi.hideShortfall(this); render(); }));
        findViewById(R.id.btnAddBudgetRow).setOnClickListener(view -> BudgetUi.pickExpenseCategory(this, book, new HashSet<>(categories),
                category -> { categories.add(category); limits.add(""); rebuildRows(); }));
        findViewById(R.id.btnSaveMonthlyBudget).setOnClickListener(view -> save());
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
        text(R.id.tvMonthlyBudgetPeriodValue, FinanceUi.monthLabel(currentMonth()));
        // Only refresh category names; rebuilding would drop focus while the user types.
        LinearLayout container = findViewById(R.id.containerBudgetRows);
        for (int i = 0; i < container.getChildCount() && i < categories.size(); i++) {
            FinanceBook.Category category = book.category(categories.get(i));
            ((TextView) container.getChildAt(i).findViewById(R.id.tvRowCategory)).setText(category == null
                    ? getString(R.string.transaction_category_placeholder) : category.name);
        }
    }

    /** Rows are rebuilt from the saved lists so input survives rotation and book refreshes. */
    private void rebuildRows() {
        LinearLayout container = findViewById(R.id.containerBudgetRows);
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < categories.size(); i++) {
            final int index = i;
            View row = inflater.inflate(R.layout.item_monthly_budget_row, container, false);
            FinanceBook.Category category = book == null ? null : book.category(categories.get(i));
            ((TextView) row.findViewById(R.id.tvRowCategory)).setText(category == null
                    ? getString(R.string.transaction_category_placeholder) : category.name);
            EditText limit = row.findViewById(R.id.etRowLimit);
            limit.setText(limits.get(i));
            limit.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
                @Override public void afterTextChanged(Editable s) {
                    if (index < limits.size()) { limits.set(index, s.toString()); BudgetUi.hideShortfall(MonthlyBudgetFormActivity.this); updateTotal(); }
                }
            });
            row.findViewById(R.id.btnRemoveRow).setOnClickListener(view -> {
                categories.remove(index); limits.remove(index); BudgetUi.hideShortfall(this); rebuildRows();
            });
            container.addView(row);
        }
        updateTotal();
    }

    /** Live total = sum of the valid row limits; it is only displayed, never sent. */
    private void updateTotal() {
        long total = 0;
        for (String value : limits) {
            try { total = Math.addExact(total, FinanceBook.parseAmount(value.trim())); }
            catch (FinanceBook.Failure | ArithmeticException ignored) { }
        }
        text(R.id.tvMonthlyBudgetTotal, getString(R.string.monthly_budget_live_total, FinanceUi.money(total)));
    }

    private void save() {
        if (book == null || model.busy()) return;
        if (categories.isEmpty()) { Toast.makeText(this, R.string.monthly_budget_category_required, Toast.LENGTH_SHORT).show(); return; }
        if (book.monthlyBudgetFor(currentMonth()) != null) { Toast.makeText(this, R.string.monthly_budget_exists, Toast.LENGTH_SHORT).show(); return; }
        List<FinanceBook.Budget> lines = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        LinearLayout container = findViewById(R.id.containerBudgetRows);
        for (int i = 0; i < categories.size(); i++) {
            long limit;
            try { limit = FinanceBook.parseAmount(limits.get(i).trim()); }
            catch (FinanceBook.Failure error) {
                Toast.makeText(this, R.string.finance_amount_error, Toast.LENGTH_SHORT).show();
                container.getChildAt(i).findViewById(R.id.etRowLimit).requestFocus(); return;
            }
            if (!seen.add(categories.get(i))) continue;
            lines.add(new FinanceBook.Budget(UUID.randomUUID().toString(), categories.get(i), currentMonth(), limit));
        }
        BudgetUi.hideShortfall(this);
        model.change(FinanceAction.monthlyBudget(id, currentMonth(), lines), true);
    }

    @Override
    protected CharSequence failure(FinanceViewModel.Command command) {
        String code = command.failure == null ? null : command.failure.code;
        if (FinanceBook.BUDGET_PERIOD_CONFLICT.equals(code)) return getString(R.string.monthly_budget_exists);
        CharSequence text = BudgetUi.failure(this, book, command);
        // Shortfall is shown inline (text null); the form keeps all input.
        return text != null || FinanceBook.BUDGET_EXCEEDS_AVAILABLE_BALANCE.equals(code) ? text : super.failure(command);
    }

    @Override
    protected void loading(boolean loading) { findViewById(R.id.btnSaveMonthlyBudget).setEnabled(!loading); }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(KEY_ID, id); out.putString(KEY_MONTH, monthChosen ? month : null);
        out.putStringArrayList(KEY_CATEGORIES, categories); out.putStringArrayList(KEY_LIMITS, limits);
    }
}
