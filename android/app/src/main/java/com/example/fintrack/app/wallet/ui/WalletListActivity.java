package com.example.fintrack.app.wallet.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.fintrack.databinding.ActivityWalletListBinding;
import com.example.fintrack.app.finance.data.FinanceRepository;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.wallet.adapter.WalletAdapter;

public class WalletListActivity extends FinanceActivity {
    private ActivityWalletListBinding binding;
    private WalletAdapter adapter;
    @Override protected View contentView() { binding = ActivityWalletListBinding.inflate(getLayoutInflater()); return binding.getRoot(); }
    @Override protected FinanceRepository.Scope readScope() { return FinanceRepository.Scope.WALLETS; }
    @Override protected void bind(Bundle saved) {
        adapter = new WalletAdapter(wallet -> startActivity(new Intent(this, WalletDetailActivity.class).putExtra(WalletDetailActivity.EXTRA_WALLET_ID, wallet.id)));
        binding.rvWallets.setLayoutManager(new LinearLayoutManager(this)); binding.rvWallets.setAdapter(adapter);
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnAddWallet.setOnClickListener(v -> startActivity(new Intent(this, WalletAddActivity.class)));
    }
    @Override protected void render() {
        adapter.setWallets(book);
        FinanceUi.visible(binding.layoutEmptyWallets, book.wallets().isEmpty());
        FinanceUi.visible(binding.rvWallets, !book.wallets().isEmpty());
    }
}
