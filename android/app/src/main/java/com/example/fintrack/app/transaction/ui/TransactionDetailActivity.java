package com.example.fintrack.app.transaction.ui;

import com.example.fintrack.app.finance.domain.Category;
import com.example.fintrack.app.finance.domain.FinanceProblem;
import com.example.fintrack.app.finance.domain.Transaction;
import com.example.fintrack.app.finance.domain.TransactionType;
import com.example.fintrack.app.finance.domain.Wallet;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.data.FinanceRepository;
import com.example.fintrack.app.finance.ui.FinanceViewModel;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.databinding.ActivityTransactionDetailBinding;

public final class TransactionDetailActivity extends FinanceActivity {
    private String id;
    private ActivityTransactionDetailBinding binding;
    @Override protected View contentView() { binding = ActivityTransactionDetailBinding.inflate(getLayoutInflater()); return binding.getRoot(); }
    @Override protected FinanceRepository.Scope readScope() { return FinanceRepository.Scope.TRANSACTION_DETAIL; }
    @Override protected String detailId() { return getIntent().getStringExtra(TransactionFormActivity.EXTRA_ID); }
    @Override protected void bind(Bundle saved) {
        id = getIntent().getStringExtra(TransactionFormActivity.EXTRA_ID); binding.toolbarTransactionDetail.setNavigationOnClickListener(view -> finish());
        binding.btnEditTransaction.setOnClickListener(view -> startActivity(new Intent(this, TransactionFormActivity.class)
                .putExtra(TransactionFormActivity.EXTRA_ID, id)));
        binding.btnDeleteTransaction.setOnClickListener(view -> confirmDelete(() -> model.change(FinanceAction.deleteTransaction(id), true)));
    }
    @Override protected void render() {
        if (book == null) return;
        Transaction value = book.transaction(id);
        if (value == null) { if (!model.saveLoading()) finish(); return; }
        boolean transfer = value.type == TransactionType.TRANSFER, voided = "VOIDED".equals(value.status);
        String zone = book.timeZone();
        binding.toolbarTransactionDetail.setTitle(transfer ? R.string.transfer_detail_title : R.string.transaction_detail_title);
        binding.tvDetailTypeBadge.setText(getString(FinanceUi.typeName(value.type)) + (voided ? " · " + getString(R.string.transaction_voided_status) : ""));
        // A VOIDED record is read-only audit history (contract section 5).
        FinanceUi.visible(binding.btnEditTransaction, !voided); FinanceUi.visible(binding.btnDeleteTransaction, !voided);
        binding.tvDetailAmount.setText((value.type == TransactionType.INCOME ? "+" : transfer ? "" : "−") + FinanceUi.money(value.amount));
        binding.tvDetailAmount.setTextColor(getColor(value.type == TransactionType.INCOME ? R.color.amount_income
                : transfer ? R.color.amount_transfer : R.color.amount_expense));
        binding.tvDetailDateTime.setText(FinanceUi.date(value.occurredAt, zone)); binding.tvDetailTime.setText(FinanceUi.date(value.occurredAt, zone));
        Category category = book.category(value.categoryId);
        binding.tvDetailCategory.setText(category == null ? "" : category.name + (category.archivedAt == null ? "" : getString(R.string.wallet_archived_suffix)));
        binding.tvDetailWallet.setText(walletName(value.walletId));
        binding.tvDetailStore.setText(value.payee); binding.tvDetailNote.setText(value.note);
        FinanceUi.visible(binding.rowCategory, !transfer); FinanceUi.visible(binding.rowWallet, !transfer); FinanceUi.visible(binding.rowStore, !transfer && !value.payee.isEmpty());
        FinanceUi.visible(binding.rowNote, !value.note.isEmpty()); FinanceUi.visible(binding.rowTransferFrom, transfer); FinanceUi.visible(binding.rowTransferTo, transfer); FinanceUi.visible(binding.rowImpact, transfer);
        if (transfer) {
            binding.tvDetailTransferFrom.setText(walletName(value.walletId)); binding.tvDetailTransferTo.setText(walletName(value.destinationWalletId));
            binding.tvDetailImpact.setText(getString(R.string.transfer_impact_value, walletName(value.walletId),
                    FinanceUi.money(value.amount), walletName(value.destinationWalletId)));
        }
    }
    /** History shows archived names; a missing reference renders empty instead of crashing. */
    private String walletName(String walletId) {
        Wallet wallet = book.wallet(walletId);
        return wallet == null ? "" : wallet.name + (wallet.archivedAt == null ? "" : getString(R.string.wallet_archived_suffix));
    }
    @Override protected CharSequence failure(FinanceViewModel.Command command) {
        // Only deleting an income (or a transfer, on its destination) can make a wallet negative; keep the screen and explain.
        if (command.error == FinanceProblem.INSUFFICIENT_FUNDS) {
            Transaction value = book == null ? null : book.transaction(id);
            return getString(value != null && value.type == TransactionType.TRANSFER
                    ? R.string.transfer_delete_insufficient : R.string.transaction_delete_insufficient);
        }
        return TransactionFormActivity.message(this, command);
    }
    @Override protected void loading(boolean loading) {
        binding.btnEditTransaction.setEnabled(!loading); binding.btnDeleteTransaction.setEnabled(!loading);
    }
}
