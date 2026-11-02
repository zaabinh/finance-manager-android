package com.example.fintrack.app.budget.ui;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.util.List;

/**
 * Adapter quản lý danh sách hiển thị các khoản ngân sách (Budget) trong RecyclerView.
 */
final class BudgetAdapter extends RecyclerView.Adapter<BudgetAdapter.Holder> {

    private final FinanceBook book;
    private final List<FinanceBook.Budget> budgets;

    // Constructor nhận dữ liệu sổ tài chính và danh sách ngân sách
    BudgetAdapter(FinanceBook book, List<FinanceBook.Budget> budgets) {
        this.book = book;
        this.budgets = budgets;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        // Khởi tạo layout từng item ngân sách từ file XML
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_budget, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        // Lấy thông tin ngân sách và tính toán mức độ sử dụng tại vị trí hiện tại
        FinanceBook.Budget budget = budgets.get(position);
        FinanceBook.BudgetUsage usage = book.usage(budget);

        // Hiển thị tên danh mục và khoảng thời gian của ngân sách
        holder.text(R.id.tvBudgetCategoryName, book.category(budget.categoryId).name);
        holder.text(R.id.tvBudgetPeriod, FinanceUi.monthLabel(budget.month).toString());

        // Hiển thị phần trăm đã chi tiêu
        holder.text(R.id.tvBudgetPercentage, usage.percentage + "%");

        // Hiển thị số tiền đã chi tiêu so với hạn mức tổng
        holder.text(R.id.tvBudgetSpent, FinanceUi.money(usage.spent));
        holder.text(R.id.tvBudgetLimit, "/ " + FinanceUi.money(budget.limit));

        // Hiển thị trạng thái ngân sách (Vượt hạn mức, Cảnh báo, hoặc Còn lại bao nhiêu)
        holder.text(R.id.tvBudgetStatus, holder.itemView.getContext().getString(
                usage.exceeded ? R.string.finance_budget_exceeded
                        : usage.warning ? R.string.finance_budget_warning
                          : R.string.finance_budget_remaining,
                FinanceUi.money(usage.remaining < 0 ? -usage.remaining : usage.remaining)
        ));

        // Cập nhật giá trị và màu sắc cho thanh tiến trình (Progress Bar)
        LinearProgressIndicator progress = holder.itemView.findViewById(R.id.progressBudget);
        progress.setProgress(usage.progress);
        progress.setIndicatorColor(holder.itemView.getContext().getColor(
                usage.exceeded ? R.color.status_error
                        : usage.warning ? R.color.status_warning
                          : R.color.color_primary
        ));

        // Đặt icon mặc định cho danh mục
        ((ImageView) holder.itemView.findViewById(R.id.ivBudgetCategoryIcon)).setImageResource(R.drawable.ic_category);

        // Xử lý sự kiện bấm vào một item để xem chi tiết ngân sách
        holder.itemView.setOnClickListener(view -> {
            Intent intent = new Intent(view.getContext(), BudgetDetailActivity.class)
                    .putExtra(BudgetFormActivity.EXTRA_ID, budget.id);
            view.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return budgets.size();
    }

    /**
     * ViewHolder lưu giữ các View thành phần của một item trong danh sách
     */
    static final class Holder extends RecyclerView.ViewHolder {
        Holder(View view) {
            super(view);
        }

        // Hàm tiện ích giúp gán nhanh chuỗi văn bản cho TextView theo ID
        void text(int id, String value) {
            ((TextView) itemView.findViewById(id)).setText(value);
        }
    }
}