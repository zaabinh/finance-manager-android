package com.example.fintrack.app.wallet.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fintrack.R;
import com.example.fintrack.app.wallet.adapter.TransactionAdapter;
import com.example.fintrack.app.wallet.model.WalletItem;
import com.example.fintrack.app.wallet.viewmodel.WalletViewModel;

public class WalletDetailActivity extends AppCompatActivity {

    private ImageView btnBack;
    private TextView btnEditWallet;
    private TextView tvWalletName;
    private TextView tvBalanceHero;
    private TextView tvIncomeThisMonth;
    private TextView tvExpenseThisMonth;
    private RecyclerView rvRecentTransactions;
    private Button btnDelete;
    private Button btnDisable;

    private WalletViewModel walletViewModel;
    private TransactionAdapter transactionAdapter;
    private String walletId;
    private WalletItem currentWallet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet_detail);

        walletId = getIntent().getStringExtra("WALLET_ID");
        walletViewModel = new ViewModelProvider(this).get(WalletViewModel.class);

        initViews();
        setupRecyclerView();
        loadData();
        setupListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnEditWallet = findViewById(R.id.btnEditWallet);
        tvWalletName = findViewById(R.id.tvWalletName);
        tvBalanceHero = findViewById(R.id.tvBalanceHero);
        tvIncomeThisMonth = findViewById(R.id.tvIncomeThisMonth);
        tvExpenseThisMonth = findViewById(R.id.tvExpenseThisMonth);
        rvRecentTransactions = findViewById(R.id.rvRecentTransactions);
        btnDelete = findViewById(R.id.btnDelete);
        btnDisable = findViewById(R.id.btnDisable);
    }

    private void setupRecyclerView() {
        transactionAdapter = new TransactionAdapter();
        rvRecentTransactions.setLayoutManager(new LinearLayoutManager(this));
        rvRecentTransactions.setAdapter(transactionAdapter);
    }

    private void loadData() {
        currentWallet = walletViewModel.getWalletById(walletId != null ? walletId : "w1");
        if (currentWallet != null) {
            tvWalletName.setText(currentWallet.getName());
            tvBalanceHero.setText(currentWallet.getFormattedBalance());
        }
        transactionAdapter.setTransactions(walletViewModel.getRecentTransactions(walletId));
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnEditWallet.setOnClickListener(v -> {
            Intent intent = new Intent(this, WalletEditActivity.class);
            intent.putExtra("WALLET_ID", currentWallet != null ? currentWallet.getId() : walletId);
            startActivity(intent);
        });

        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Xoá ví")
                    .setMessage("Bạn có chắc chắn muốn xoá ví này?")
                    .setPositiveButton("Xoá", (dialog, which) -> {
                        if (currentWallet != null) {
                            walletViewModel.deleteWallet(currentWallet.getId());
                        }
                        Toast.makeText(this, "Đã xoá ví thành công", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .setNegativeButton("Huỷ", (dialog, which) -> dialog.dismiss())
                    .show();
        });

        btnDisable.setOnClickListener(v -> {
            Toast.makeText(this, "Đã vô hiệu hoá ví", Toast.LENGTH_SHORT).show();
        });
    }
}
