package com.example.fintrack.app.wallet.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fintrack.R;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.Wallet;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.wallet.ui.WalletTypes;
import com.example.fintrack.databinding.ItemWalletBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * WalletAdapter: Adapter quản lý danh sách Wallet trong màn hình quản lý ví.
 * Sử dụng WalletTypes.label và DiffUtil để tối ưu hiệu năng render danh sách ví.
 */
public class WalletAdapter extends RecyclerView.Adapter<WalletAdapter.Holder> {
    public interface OnWalletClickListener { void onWalletClick(Wallet wallet); }

    private final List<Wallet> wallets = new ArrayList<>();
    private FinanceBook book;
    private final OnWalletClickListener listener;

    public WalletAdapter(OnWalletClickListener listener) {
        this.listener = listener;
    }

    public void setWallets(FinanceBook newBook) {
        FinanceBook oldBook = this.book;
        this.book = newBook;
        List<Wallet> next = newBook != null ? newBook.wallets() : new ArrayList<>();

        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return wallets.size(); }
            @Override public int getNewListSize() { return next.size(); }
            @Override public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return wallets.get(oldItemPosition).id.equals(next.get(newItemPosition).id);
            }
            @Override public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Wallet oldW = wallets.get(oldItemPosition);
                Wallet newW = next.get(newItemPosition);
                long oldBal = oldBook != null ? oldBook.balance(oldW.id) : 0;
                long newBal = newBook != null ? newBook.balance(newW.id) : 0;
                return Objects.equals(oldW.name, newW.name)
                        && oldW.walletType == newW.walletType
                        && Objects.equals(oldW.archivedAt, newW.archivedAt)
                        && oldBal == newBal;
            }
        });

        wallets.clear();
        wallets.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(ItemWalletBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Wallet wallet = wallets.get(position);
        holder.binding.tvWalletName.setText(wallet.name);
        holder.binding.tvWalletSubtitle.setText(holder.itemView.getContext().getString(WalletTypes.label(wallet.walletType))
                + (wallet.archivedAt == null ? "" : holder.itemView.getContext().getString(R.string.wallet_archived_suffix)));
        holder.binding.tvWalletBalance.setText(FinanceUi.money(book.balance(wallet.id)));
        holder.itemView.setOnClickListener(v -> listener.onWalletClick(wallet));
    }

    @Override
    public int getItemCount() {
        return wallets.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemWalletBinding binding;
        Holder(ItemWalletBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
