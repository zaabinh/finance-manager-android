package com.example.fintrack.app.wallet.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.ui.FinanceUi;
import java.util.ArrayList;
import java.util.List;

public class WalletAdapter extends RecyclerView.Adapter<WalletAdapter.Holder> {
    public interface OnWalletClickListener { void onWalletClick(FinanceBook.Wallet wallet); }
    private final List<FinanceBook.Wallet> wallets = new ArrayList<>();
    private FinanceBook book;
    private final OnWalletClickListener listener;
    public WalletAdapter(OnWalletClickListener listener) { this.listener=listener; }
    public void setWallets(FinanceBook book) { this.book=book; wallets.clear(); wallets.addAll(book.state().wallets); notifyDataSetChanged(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_wallet,parent,false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder,int position) {
        FinanceBook.Wallet wallet=wallets.get(position); holder.name.setText(wallet.name);
        int label = wallet.walletType == FinanceBook.WalletType.BANK ? R.string.wallet_type_bank
                : wallet.walletType == FinanceBook.WalletType.SAVINGS ? R.string.wallet_type_savings : R.string.wallet_type_cash;
        holder.subtitle.setText(holder.itemView.getContext().getString(label)
                + (wallet.archivedAt == null ? "" : holder.itemView.getContext().getString(R.string.wallet_archived_suffix)));
        holder.balance.setText(FinanceUi.money(book.balance(wallet.id)));
        holder.itemView.setOnClickListener(v -> listener.onWalletClick(wallet));
    }
    @Override public int getItemCount() { return wallets.size(); }
    static class Holder extends RecyclerView.ViewHolder {
        final TextView name, subtitle, balance;
        Holder(View view) { super(view); name=view.findViewById(R.id.tvWalletName); subtitle=view.findViewById(R.id.tvWalletSubtitle); balance=view.findViewById(R.id.tvWalletBalance); }
    }
}
