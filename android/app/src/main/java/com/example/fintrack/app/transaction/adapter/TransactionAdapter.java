package com.example.fintrack.app.transaction.adapter;

import com.example.fintrack.app.finance.domain.Category;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.Transaction;
import com.example.fintrack.app.finance.domain.TransactionType;
import com.example.fintrack.app.finance.domain.Wallet;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.transaction.ui.TransactionDetailActivity;
import com.example.fintrack.app.transaction.ui.TransactionFormActivity;
import com.example.fintrack.databinding.ItemTransactionBinding;
import java.util.List;

/**
 * Adapter quản lý danh sách hiển thị các giao dịch (Transaction) trong RecyclerView.
 */
public final class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.Holder> {

    private FinanceBook book;
    private List<Transaction> transactions;

    public TransactionAdapter(FinanceBook book, List<Transaction> transactions) {
        this.book = book;
        this.transactions = transactions;
    }

    /** Replaces rows in place so appending a page keeps the scroll position. */
    public void submit(FinanceBook book, List<Transaction> transactions) {
        this.book = book;
        this.transactions = transactions;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(ItemTransactionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Transaction item = transactions.get(position);
        Category category = book.category(item.categoryId);
        Wallet wallet = book.wallet(item.walletId), destination = book.wallet(item.destinationWalletId);
        ItemTransactionBinding row = holder.binding;
        row.tvTransactionTitle.setText(!item.payee.isEmpty() ? item.payee
                : category != null ? category.name : holder.itemView.getContext().getString(R.string.transaction_type_transfer));
        row.tvTransactionMeta.setText((wallet == null ? "" : wallet.name)
                + (destination == null ? "" : " → " + destination.name) + " · " + FinanceUi.date(item.occurredAt, book.timeZone()));
        row.tvTransactionSign.setText(item.type == TransactionType.INCOME ? "+" : item.type == TransactionType.EXPENSE ? "−" : "");
        row.tvTransactionAmount.setText(FinanceUi.money(item.amount));
        int color = holder.itemView.getContext().getColor(item.type == TransactionType.INCOME ? R.color.amount_income
                : item.type == TransactionType.EXPENSE ? R.color.amount_expense : R.color.amount_transfer);
        row.tvTransactionAmount.setTextColor(color);
        row.tvTransactionSign.setTextColor(color);
        ImageView icon = row.ivTransactionIcon;
        if (item.type == TransactionType.TRANSFER) {
            icon.setImageResource(R.drawable.ic_arrow_left_right);
            // Reset: a recycled row may still carry a category colour.
            icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                    holder.itemView.getContext().getColor(R.color.text_secondary)));
        } else {
            com.example.fintrack.app.category.model.CategoryIcons.paint(icon, book.category(item.categoryId));
        }
        holder.itemView.setOnClickListener(view -> view.getContext().startActivity(new Intent(view.getContext(), TransactionDetailActivity.class)
                .putExtra(TransactionFormActivity.EXTRA_ID, item.id)));
    }

    @Override
    public int getItemCount() {
        return transactions.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemTransactionBinding binding;

        Holder(ItemTransactionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
