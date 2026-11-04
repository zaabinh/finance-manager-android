package com.example.fintrack.app.budget.ui;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.google.android.material.appbar.MaterialToolbar;
import java.util.UUID;

/**
 * Màn hình Form Ngân sách (BudgetFormActivity):
 * Dùng để người dùng Thiết lập mới hoặc Chỉnh sửa một khoản ngân sách.
 */
public final class BudgetFormActivity extends FinanceActivity {

    public static final String EXTRA_ID = "budget_id";
    private String id, category, month = FinanceBook.month(System.currentTimeMillis());
    private boolean editing, initialized;

    @Override 
    protected int layout() { return R.layout.activity_budget_form; }

    @Override 
    protected void bind(Bundle saved) {
        toolbar(R.id.toolbarBudgetForm); editing = getIntent().hasExtra(EXTRA_ID);
        id = editing ? getIntent().getStringExtra(EXTRA_ID) : UUID.randomUUID().toString();
        if (getIntent().hasExtra("month")) month = getIntent().getStringExtra("month");
        if (saved != null) { initialized = saved.getBoolean("initialized"); id = saved.getString("id"); category = saved.getString("category"); month = saved.getString("month"); }
        
        ((MaterialToolbar) findViewById(R.id.toolbarBudgetForm)).setTitle(editing ? R.string.budget_edit_title : R.string.budget_add_title);
        text(R.id.tvAmountLabel, getString(R.string.budget_limit_label)); visible(R.id.tvAmountSign, false);
        text(R.id.btnSaveBudget, getString(editing ? R.string.btn_save_changes : R.string.btn_create_budget));
        
        // Đăng ký sự kiện chọn danh mục chi tiêu, tháng áp dụng và lưu
        findViewById(R.id.fieldBudgetCategory).setOnClickListener(view -> FinanceUi.category(this, FinanceBook.Type.EXPENSE, value -> { category = value; render(); }));
        findViewById(R.id.fieldBudgetPeriod).setOnClickListener(view -> FinanceUi.month(this, month, value -> { month = value; render(); }));
        findViewById(R.id.btnSaveBudget).setOnClickListener(view -> save());
    }

    @Override 
    protected void render() {
        if (book == null) return;
        if (!initialized) {
            if (editing) {
                FinanceBook.Budget value = book.budget(id);
                if (value == null) { Toast.makeText(this, R.string.finance_not_found, Toast.LENGTH_LONG).show(); finish(); return; }
                category = value.categoryId; month = value.month; text(R.id.etAmount, Long.toString(value.limit));
            }
            initialized = true;
        }
        text(R.id.tvBudgetCatValue, book.category(category) == null ? getString(R.string.transaction_category_placeholder) : book.category(category).name);
        text(R.id.tvBudgetPeriodValue, FinanceUi.monthLabel(month));
    }

    /** Xử lý kiểm tra dữ liệu và lưu ngân sách */
    private void save() {
        if (book == null || model.busy()) return;
        
        // 1. Kiểm tra bắt buộc chọn danh mục
        if (category == null || category.trim().isEmpty()) {
            Toast.makeText(this, R.string.error_required_category, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 2. Kiểm tra chống tạo trùng ngân sách cùng danh mục trong tháng
        for (FinanceBook.Budget item : book.state().budgets) {
            if ((!editing || !item.id.equals(id)) && item.categoryId.equals(category) && item.month.equals(month)) {
                Toast.makeText(this, R.string.error_duplicate_budget, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        
        // 3. Phân tích số tiền hạn mức
        long limit;
        try { limit = FinanceBook.parseAmount(((EditText) findViewById(R.id.etAmount)).getText().toString()); }
        catch (FinanceBook.Failure error) {
            text(R.id.tvAmountError, getString(R.string.finance_amount_error)); visible(R.id.tvAmountError, true);
            findViewById(R.id.etAmount).requestFocus(); return;
        }
        visible(R.id.tvAmountError, false);
        
        // 4. Tạo đối tượng Budget và gửi lệnh thay đổi qua ViewModel
        FinanceBook.Budget value = new FinanceBook.Budget(id, category, month, limit);
        model.change(FinanceAction.budget(value, editing), true);
    }

    @Override 
    protected void loading(boolean loading) { findViewById(R.id.btnSaveBudget).setEnabled(!loading); }

    @Override 
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putString("id", id); out.putString("category", category); out.putString("month", month); out.putBoolean("initialized", initialized);
    }
}
