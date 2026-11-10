package com.example.fintrack.app.budget.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fintrack.R;
import com.example.fintrack.app.budget.ui.BudgetDetailActivity;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.util.List;

/** Danh sách ngân sách tháng: tháng, tổng (dẫn xuất), đã chi, còn lại và tiến độ. Dùng lại layout item_budget. */
public final class MonthlyBudgetAdapter extends RecyclerView.Adapter<MonthlyBudgetAdapter.Holder> {
    private final FinanceBook book;
    private final List<FinanceBook.MonthlyBudget> items;

    public MonthlyBudgetAdapter(FinanceBook book, List<FinanceBook.MonthlyBudget> items) {
        this.book = book;
        this.items = items;
    }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_budget, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        FinanceBook.MonthlyBudget monthly = items.get(position);
        Context context = holder.itemView.getContext();
        FinanceBook.BudgetUsage usage = book.monthlyUsage(monthly);
        long total = book.monthlyTotal(monthly);
        int count = book.categoryBudgets(monthly).size();

        holder.text(R.id.tvBudgetCategoryName, FinanceUi.monthLabel(monthly.month).toString());
        holder.text(R.id.tvBudgetPeriod, context.getResources().getQuantityString(R.plurals.monthly_budget_category_count, count, count));
        holder.text(R.id.tvBudgetPercentage, usage.percentage + "%");
        holder.text(R.id.tvBudgetSpent, FinanceUi.money(usage.spent));
        holder.text(R.id.tvBudgetLimit, "/ " + FinanceUi.money(total));
        holder.text(R.id.tvBudgetStatus, context.getString(usage.exceeded ? R.string.finance_budget_exceeded
                : usage.warning ? R.string.finance_budget_warning : R.string.finance_budget_remaining,
                FinanceUi.money(usage.remaining < 0 ? -usage.remaining : usage.remaining)));
        LinearProgressIndicator progress = holder.itemView.findViewById(R.id.progressBudget);
        progress.setProgress(usage.progress);
        progress.setIndicatorColor(context.getColor(usage.exceeded ? R.color.status_error
                : usage.warning ? R.color.status_warning : R.color.color_primary));
        ((ImageView) holder.itemView.findViewById(R.id.ivBudgetCategoryIcon)).setImageResource(R.drawable.ic_calendar);
        holder.itemView.setOnClickListener(view -> view.getContext().startActivity(new Intent(view.getContext(), BudgetDetailActivity.class)
                .putExtra(BudgetDetailActivity.EXTRA_MONTHLY_ID, monthly.id)));
    }

    @Override public int getItemCount() { return items.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        Holder(View view) { super(view); }
        void text(int id, String value) { ((TextView) itemView.findViewById(id)).setText(value); }
    }
}
