package com.example.fintrack.app.budget.adapter;

import com.example.fintrack.app.finance.domain.Budget;
import com.example.fintrack.app.finance.domain.BudgetUsage;
import com.example.fintrack.app.finance.domain.Category;
import com.example.fintrack.app.finance.domain.FinanceBook;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.databinding.ItemBudgetBinding;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.util.List;

/**
 * Adapter quản lý danh sách hiển thị các khoản ngân sách (Budget) trong RecyclerView.
 */
public final class BudgetAdapter extends RecyclerView.Adapter<BudgetAdapter.Holder> {

    private final FinanceBook book;
    private final List<Budget> budgets;
    private final java.util.function.Consumer<Budget> onClick;

    // Constructor nhận dữ liệu sổ tài chính và danh sách ngân sách danh mục (thuộc một ngân sách tháng)
    public BudgetAdapter(FinanceBook book, List<Budget> budgets, java.util.function.Consumer<Budget> onClick) {
        this.book = book;
        this.budgets = budgets;
        this.onClick = onClick;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        // Khởi tạo layout từng item ngân sách từ file XML
        return new Holder(ItemBudgetBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        // Lấy thông tin ngân sách và tính toán mức độ sử dụng tại vị trí hiện tại
        Budget budget = budgets.get(position);
        BudgetUsage usage = book.usage(budget);

        // Hiển thị tên danh mục và khoảng thời gian của ngân sách
        Category category = book.category(budget.categoryId);
        ItemBudgetBinding row = holder.binding;
        row.tvBudgetCategoryName.setText(category == null
                ? holder.itemView.getContext().getString(R.string.transaction_category_placeholder) : category.name);
        row.tvBudgetPeriod.setText(FinanceUi.monthLabel(budget.month));

        // Hiển thị phần trăm đã chi tiêu
        row.tvBudgetPercentage.setText(usage.percentage + "%");

        // Hiển thị số tiền đã chi tiêu so với hạn mức tổng
        row.tvBudgetSpent.setText(FinanceUi.money(usage.spent));
        row.tvBudgetLimit.setText("/ " + FinanceUi.money(budget.limit));

        // Hiển thị trạng thái ngân sách (Vượt hạn mức, Cảnh báo, hoặc Còn lại bao nhiêu)
        row.tvBudgetStatus.setText(holder.itemView.getContext().getString(
                usage.exceeded ? R.string.finance_budget_exceeded
                        : usage.warning ? R.string.finance_budget_warning
                          : R.string.finance_budget_remaining,
                FinanceUi.money(usage.remaining < 0 ? -usage.remaining : usage.remaining)
        ));

        // Cập nhật giá trị và màu sắc cho thanh tiến trình (Progress Bar)
        LinearProgressIndicator progress = row.progressBudget;
        progress.setProgress(usage.progress);
        progress.setIndicatorColor(holder.itemView.getContext().getColor(
                usage.exceeded ? R.color.status_error
                        : usage.warning ? R.color.status_warning
                          : R.color.color_primary
        ));

        // Icon và màu người dùng đã chọn cho danh mục
        com.example.fintrack.app.category.model.CategoryIcons.paint(row.ivBudgetCategoryIcon, category);

        // Bấm vào một ngân sách danh mục: màn hình cha quyết định (sửa hạn mức / xóa)
        holder.itemView.setOnClickListener(onClick == null ? null : view -> onClick.accept(budget));
        holder.itemView.setClickable(onClick != null);
    }

    @Override
    public int getItemCount() {
        return budgets.size();
    }

    /**
     * ViewHolder lưu giữ các View thành phần của một item trong danh sách
     */
    static final class Holder extends RecyclerView.ViewHolder {
        final ItemBudgetBinding binding;

        Holder(ItemBudgetBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
