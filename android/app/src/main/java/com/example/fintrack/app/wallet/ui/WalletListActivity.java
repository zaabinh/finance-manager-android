package com.example.fintrack.app.wallet.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fintrack.R;
import com.example.fintrack.app.wallet.adapter.WalletAdapter;
import com.example.fintrack.app.wallet.viewmodel.WalletViewModel;

// Activity này chịu trách nhiệm hiển thị danh sách tất cả các ví tiền mà người dùng đang sở hữu
public class WalletListActivity extends AppCompatActivity {

    private ImageView btnBack;
    private TextView btnAddWallet;
    private RecyclerView rvWallets;
    private android.view.View layoutEmptyWallets;

    private WalletViewModel walletViewModel;
    private WalletAdapter walletAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet_list);

        walletViewModel = new ViewModelProvider(this).get(WalletViewModel.class);

        initViews();
        setupRecyclerView();
        setupObservers();
        setupListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnAddWallet = findViewById(R.id.btnAddWallet);
        rvWallets = findViewById(R.id.rvWallets);
        layoutEmptyWallets = findViewById(R.id.layoutEmptyWallets);
    }

    private void setupRecyclerView() {
        walletAdapter = new WalletAdapter(wallet -> {
            Intent intent = new Intent(WalletListActivity.this, WalletDetailActivity.class);
            intent.putExtra("WALLET_ID", wallet.getId());
            startActivity(intent);
        });

        rvWallets.setLayoutManager(new LinearLayoutManager(this));
        rvWallets.setAdapter(walletAdapter);
    }

    private void setupObservers() {
        walletViewModel.getWallets().observe(this, wallets -> {
            if (wallets != null) {
                walletAdapter.setWallets(wallets);
                boolean isEmpty = wallets.isEmpty();
                if (layoutEmptyWallets != null) {
                    layoutEmptyWallets.setVisibility(isEmpty ? android.view.View.VISIBLE : android.view.View.GONE);
                }
                rvWallets.setVisibility(isEmpty ? android.view.View.GONE : android.view.View.VISIBLE);
            }
        });
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnAddWallet.setOnClickListener(v -> {
            Intent intent = new Intent(WalletListActivity.this, WalletAddActivity.class);
            startActivity(intent);
        });
    }
}
