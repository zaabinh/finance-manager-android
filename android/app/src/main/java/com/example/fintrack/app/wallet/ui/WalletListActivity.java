package com.example.fintrack.app.wallet.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.data.FinanceRepository;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.wallet.adapter.WalletAdapter;

public class WalletListActivity extends FinanceActivity {
    private WalletAdapter adapter;
    @Override protected int layout() { return R.layout.activity_wallet_list; }
    @Override protected FinanceRepository.Scope readScope() { return FinanceRepository.Scope.WALLETS; }
    @Override protected void bind(Bundle saved) {
        adapter = new WalletAdapter(wallet -> startActivity(new Intent(this, WalletDetailActivity.class).putExtra("WALLET_ID", wallet.id)));
        RecyclerView list = findViewById(R.id.rvWallets);
        list.setLayoutManager(new LinearLayoutManager(this)); list.setAdapter(adapter);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAddWallet).setOnClickListener(v -> startActivity(new Intent(this, WalletAddActivity.class)));
    }
    @Override protected void render() {
        adapter.setWallets(book);
        visible(R.id.layoutEmptyWallets, book.state().wallets.isEmpty());
        visible(R.id.rvWallets, !book.state().wallets.isEmpty());
    }
}
