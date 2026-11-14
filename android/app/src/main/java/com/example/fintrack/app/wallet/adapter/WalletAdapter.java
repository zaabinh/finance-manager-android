package com.example.fintrack.app.wallet.adapter;

import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.Wallet;
import com.example.fintrack.app.finance.domain.WalletType;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.databinding.ItemWalletBinding;
import java.util.ArrayList;
import java.util.List;

public class WalletAdapter extends RecyclerView.Adapter<WalletAdapter.Holder> {
    public interface OnWalletClickListener { void onWalletClick(Wallet wallet); }
    private final List<Wallet> wallets = new ArrayList<>();
    private FinanceBook book;
    private final OnWalletClickListener listener;
    public WalletAdapter(OnWalletClickListener listener) { this.listener=listener; }
    public void setWallets(FinanceBook book) { this.book=book; wallets.clear(); wallets.addAll(book.wallets()); notifyDataSetChanged(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(ItemWalletBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder,int position) {
        Wallet wallet=wallets.get(position); holder.binding.tvWalletName.setText(wallet.name);
        int label = wallet.walletType == WalletType.BANK ? R.string.wallet_type_bank
                : wallet.walletType == WalletType.SAVINGS ? R.string.wallet_type_savings : R.string.wallet_type_cash;
        holder.binding.tvWalletSubtitle.setText(holder.itemView.getContext().getString(label)
                + (wallet.archivedAt == null ? "" : holder.itemView.getContext().getString(R.string.wallet_archived_suffix)));
        holder.binding.tvWalletBalance.setText(FinanceUi.money(book.balance(wallet.id)));
        holder.itemView.setOnClickListener(v -> listener.onWalletClick(wallet));
    }
    @Override public int getItemCount() { return wallets.size(); }
    static class Holder extends RecyclerView.ViewHolder {
        final ItemWalletBinding binding;
        Holder(ItemWalletBinding binding) { super(binding.getRoot()); this.binding = binding; }
    }
}
