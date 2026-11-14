package com.example.fintrack.app.budget.adapter;

import com.example.fintrack.app.finance.domain.BudgetUsage;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.MonthlyBudget;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fintrack.R;
import com.example.fintrack.app.budget.ui.BudgetDetailActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.databinding.ItemBudgetBinding;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.util.List;

/** Danh sách ngân sách tháng: tháng, tổng (dẫn xuất), đã chi, còn lại và tiến độ. Dùng lại layout item_budget. */
public final class MonthlyBudgetAdapter extends RecyclerView.Adapter<MonthlyBudgetAdapter.Holder> {
    private final FinanceBook book;
    private final List<MonthlyBudget> items;

    public MonthlyBudgetAdapter(FinanceBook book, List<MonthlyBudget> items) {
        this.book = book;
        this.items = items;
    }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(ItemBudgetBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        MonthlyBudget monthly = items.get(position);
        Context context = holder.itemView.getContext();
        BudgetUsage usage = book.monthlyUsage(monthly);
        long total = book.monthlyTotal(monthly);
        int count = book.categoryBudgets(monthly).size();

        ItemBudgetBinding row = holder.binding;
        row.tvBudgetCategoryName.setText(FinanceUi.monthLabel(context, monthly.month));
        row.tvBudgetPeriod.setText(context.getResources().getQuantityString(R.plurals.monthly_budget_category_count, count, count));
        row.tvBudgetPercentage.setText(usage.percentage + "%");
        row.tvBudgetSpent.setText(FinanceUi.money(usage.spent));
        row.tvBudgetLimit.setText("/ " + FinanceUi.money(total));
        row.tvBudgetStatus.setText(context.getString(usage.exceeded ? R.string.finance_budget_exceeded
                : usage.warning ? R.string.finance_budget_warning : R.string.finance_budget_remaining,
                FinanceUi.money(usage.remaining < 0 ? -usage.remaining : usage.remaining)));
        LinearProgressIndicator progress = row.progressBudget;
        progress.setProgress(usage.progress);
        progress.setIndicatorColor(context.getColor(usage.exceeded ? R.color.status_error
                : usage.warning ? R.color.status_warning : R.color.color_primary));
        row.ivBudgetCategoryIcon.setImageResource(R.drawable.ic_calendar);
        holder.itemView.setOnClickListener(view -> view.getContext().startActivity(new Intent(view.getContext(), BudgetDetailActivity.class)
                .putExtra(BudgetDetailActivity.EXTRA_MONTHLY_ID, monthly.id)));
    }

    @Override public int getItemCount() { return items.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemBudgetBinding binding;
        Holder(ItemBudgetBinding binding) { super(binding.getRoot()); this.binding = binding; }
    }
}
