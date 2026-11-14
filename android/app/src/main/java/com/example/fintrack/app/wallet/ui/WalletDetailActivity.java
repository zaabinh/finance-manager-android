package com.example.fintrack.app.wallet.ui;

import com.example.fintrack.app.finance.domain.Wallet;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.data.FinanceRepository;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.transaction.ui.TransactionListActivity;
import com.example.fintrack.databinding.ActivityWalletDetailBinding;

public class WalletDetailActivity extends FinanceActivity {
    /** Wallet id; also read by WalletEditActivity and, as a filter, by TransactionListActivity. */
    public static final String EXTRA_WALLET_ID = "WALLET_ID";
    private ActivityWalletDetailBinding binding;
    private String id;
    private Wallet wallet;
    @Override protected View contentView() { binding = ActivityWalletDetailBinding.inflate(getLayoutInflater()); return binding.getRoot(); }
    @Override protected FinanceRepository.Scope readScope() { return FinanceRepository.Scope.WALLETS; }
    @Override protected void bind(Bundle saved) {
        id = getIntent().getStringExtra(EXTRA_WALLET_ID);
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnEditWallet.setOnClickListener(v -> {
            if (wallet != null && wallet.archivedAt == null)
                startActivity(new Intent(this, WalletEditActivity.class).putExtra(EXTRA_WALLET_ID, id));
        });
        binding.btnDelete.setOnClickListener(v -> {
            if (wallet != null) confirmDelete(() -> model.change(FinanceAction.walletArchive(id), false));
        });
        binding.btnDisable.setOnClickListener(v -> {
            if (wallet != null) model.change(FinanceAction.walletStatus(id, wallet.archivedAt != null), false);
        });
        binding.btnSeeAllTransactions.setOnClickListener(v ->
                startActivity(new Intent(this, TransactionListActivity.class).putExtra(EXTRA_WALLET_ID, id)));
        // A transaction page cannot provide exact monthly totals.
        binding.rvRecentTransactions.setVisibility(View.GONE);
        ((View) binding.tvIncomeThisMonth.getParent().getParent()).setVisibility(View.GONE);
    }
    @Override protected void render() {
        wallet = book.wallet(id);
        if (wallet == null) { Toast.makeText(this, R.string.wallet_missing_or_archived, Toast.LENGTH_LONG).show(); finish(); return; }
        binding.tvWalletName.setText(wallet.name + (wallet.archivedAt == null ? "" : getString(R.string.wallet_archived_suffix)));
        binding.tvBalanceHero.setText(FinanceUi.money(book.balance(id)));
        FinanceUi.visible(binding.btnEditWallet, wallet.archivedAt == null);
        binding.btnDelete.setVisibility(View.GONE);
        binding.btnDisable.setText(getString(wallet.archivedAt == null ? R.string.wallet_archive : R.string.wallet_restore));
    }
    @Override protected void completed() { model.refresh(); }
}
