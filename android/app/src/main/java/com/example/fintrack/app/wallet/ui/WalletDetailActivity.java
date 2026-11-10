package com.example.fintrack.app.wallet.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.data.FinanceRepository;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.transaction.ui.TransactionListActivity;

public class WalletDetailActivity extends FinanceActivity {
    private String id;
    private FinanceBook.Wallet wallet;
    @Override protected int layout() { return R.layout.activity_wallet_detail; }
    @Override protected FinanceRepository.Scope readScope() { return FinanceRepository.Scope.WALLETS; }
    @Override protected void bind(Bundle saved) {
        id = getIntent().getStringExtra("WALLET_ID");
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnEditWallet).setOnClickListener(v -> {
            if (wallet != null && wallet.archivedAt == null)
                startActivity(new Intent(this, WalletEditActivity.class).putExtra("WALLET_ID", id));
        });
        findViewById(R.id.btnDelete).setOnClickListener(v -> {
            if (wallet != null) confirmDelete(() -> model.change(FinanceAction.walletArchive(id), false));
        });
        findViewById(R.id.btnDisable).setOnClickListener(v -> {
            if (wallet != null) model.change(FinanceAction.walletStatus(id, wallet.archivedAt != null), false);
        });
        findViewById(R.id.btnSeeAllTransactions).setOnClickListener(v ->
                startActivity(new Intent(this, TransactionListActivity.class).putExtra("WALLET_ID", id)));
        // A transaction page cannot provide exact monthly totals.
        visible(R.id.rvRecentTransactions, false);
        ((android.view.View)findViewById(R.id.tvIncomeThisMonth).getParent().getParent()).setVisibility(android.view.View.GONE);
    }
    @Override protected void render() {
        wallet = book.wallet(id);
        if (wallet == null) { Toast.makeText(this, R.string.wallet_missing_or_archived, Toast.LENGTH_LONG).show(); finish(); return; }
        text(R.id.tvWalletName, wallet.name + (wallet.archivedAt == null ? "" : getString(R.string.wallet_archived_suffix)));
        text(R.id.tvBalanceHero, FinanceUi.money(book.balance(id)));
        visible(R.id.btnEditWallet, wallet.archivedAt == null);
        visible(R.id.btnDelete, false);
        text(R.id.btnDisable, getString(wallet.archivedAt == null ? R.string.wallet_archive : R.string.wallet_restore));
    }
    @Override protected void completed() { model.refresh(); }
}
