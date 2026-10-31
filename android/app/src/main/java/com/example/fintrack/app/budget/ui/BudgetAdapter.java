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

final class BudgetAdapter extends RecyclerView.Adapter<BudgetAdapter.Holder> {
    private final FinanceBook book;
    private final List<FinanceBook.Budget> budgets;
    BudgetAdapter(FinanceBook book, List<FinanceBook.Budget> budgets) { this.book = book; this.budgets = budgets; }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_budget, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        FinanceBook.Budget budget = budgets.get(position);
        FinanceBook.BudgetUsage usage = book.usage(budget);
        holder.text(R.id.tvBudgetCategoryName, book.category(budget.categoryId).name);
        holder.text(R.id.tvBudgetPeriod, holder.itemView.getContext().getString(R.string.transaction_month_format, FinanceUi.monthLabel(budget.month)));
        holder.text(R.id.tvBudgetPercentage, usage.percentage + "%");
        holder.text(R.id.tvBudgetSpent, FinanceUi.money(usage.spent)); holder.text(R.id.tvBudgetLimit, "/ " + FinanceUi.money(budget.limit));
        holder.text(R.id.tvBudgetStatus, holder.itemView.getContext().getString(usage.exceeded ? R.string.finance_budget_exceeded
                : usage.warning ? R.string.finance_budget_warning : R.string.finance_budget_remaining,
                FinanceUi.money(usage.remaining < 0 ? -usage.remaining : usage.remaining)));
        LinearProgressIndicator progress = holder.itemView.findViewById(R.id.progressBudget);
        progress.setProgress(usage.progress);
        progress.setIndicatorColor(holder.itemView.getContext().getColor(usage.exceeded ? R.color.status_error
                : usage.warning ? R.color.status_warning : R.color.color_primary));
        ((ImageView) holder.itemView.findViewById(R.id.ivBudgetCategoryIcon)).setImageResource(R.drawable.ic_category);
        holder.itemView.setOnClickListener(view -> view.getContext().startActivity(new Intent(view.getContext(), BudgetDetailActivity.class)
                .putExtra(BudgetFormActivity.EXTRA_ID, budget.id)));
    }
    @Override public int getItemCount() { return budgets.size(); }
    static final class Holder extends RecyclerView.ViewHolder {
        Holder(View view) { super(view); }
        void text(int id, String value) { ((TextView) itemView.findViewById(id)).setText(value); }
    }
}
