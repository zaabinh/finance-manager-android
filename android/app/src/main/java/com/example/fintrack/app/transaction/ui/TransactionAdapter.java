package com.example.fintrack.app.transaction.ui;

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
import java.util.List;

public final class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.Holder> {
    private final FinanceBook book;
    private final List<FinanceBook.Transaction> transactions;
    public TransactionAdapter(FinanceBook book, List<FinanceBook.Transaction> transactions) { this.book = book; this.transactions = transactions; }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        FinanceBook.Transaction item = transactions.get(position);
        FinanceBook.Category category = book.category(item.categoryId);
        FinanceBook.Wallet wallet = book.wallet(item.walletId), destination = book.wallet(item.destinationWalletId);
        holder.text(R.id.tvTransactionTitle, !item.payee.isEmpty() ? item.payee
                : category != null ? category.name : holder.itemView.getContext().getString(R.string.transaction_type_transfer));
        holder.text(R.id.tvTransactionMeta, (wallet == null ? "" : wallet.name)
                + (destination == null ? "" : " → " + destination.name) + " · " + FinanceUi.date(item.occurredAt));
        holder.text(R.id.tvTransactionSign, item.type == FinanceBook.Type.INCOME ? "+" : item.type == FinanceBook.Type.EXPENSE ? "−" : "");
        holder.text(R.id.tvTransactionAmount, FinanceUi.money(item.amount));
        int color = holder.itemView.getContext().getColor(item.type == FinanceBook.Type.INCOME ? R.color.amount_income
                : item.type == FinanceBook.Type.EXPENSE ? R.color.amount_expense : R.color.amount_transfer);
        ((TextView) holder.itemView.findViewById(R.id.tvTransactionAmount)).setTextColor(color);
        ((TextView) holder.itemView.findViewById(R.id.tvTransactionSign)).setTextColor(color);
        ((ImageView) holder.itemView.findViewById(R.id.ivTransactionIcon)).setImageResource(
                item.type == FinanceBook.Type.TRANSFER ? R.drawable.ic_arrow_left_right : R.drawable.ic_banknote);
        holder.itemView.setOnClickListener(view -> view.getContext().startActivity(new Intent(view.getContext(), TransactionDetailActivity.class)
                .putExtra(TransactionFormActivity.EXTRA_ID, item.id)));
    }
    @Override public int getItemCount() { return transactions.size(); }
    static final class Holder extends RecyclerView.ViewHolder {
        Holder(View view) { super(view); }
        void text(int id, String text) { ((TextView) itemView.findViewById(id)).setText(text); }
    }
}
