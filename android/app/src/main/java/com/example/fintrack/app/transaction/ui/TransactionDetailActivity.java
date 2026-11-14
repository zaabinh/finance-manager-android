package com.example.fintrack.app.transaction.ui;

import com.example.fintrack.app.finance.domain.Category;
import com.example.fintrack.app.finance.domain.FinanceProblem;
import com.example.fintrack.app.finance.domain.Transaction;
import com.example.fintrack.app.finance.domain.TransactionType;
import com.example.fintrack.app.finance.domain.Wallet;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.data.FinanceRepository;
import com.example.fintrack.app.finance.ui.FinanceViewModel;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.google.android.material.appbar.MaterialToolbar;

public final class TransactionDetailActivity extends FinanceActivity {
    private String id;
    @Override protected int layout() { return R.layout.activity_transaction_detail; }
    @Override protected FinanceRepository.Scope readScope() { return FinanceRepository.Scope.TRANSACTION_DETAIL; }
    @Override protected String detailId() { return getIntent().getStringExtra(TransactionFormActivity.EXTRA_ID); }
    @Override protected void bind(Bundle saved) {
        id = getIntent().getStringExtra(TransactionFormActivity.EXTRA_ID); toolbar(R.id.toolbarTransactionDetail);
        findViewById(R.id.btnEditTransaction).setOnClickListener(view -> startActivity(new Intent(this, TransactionFormActivity.class)
                .putExtra(TransactionFormActivity.EXTRA_ID, id)));
        findViewById(R.id.btnDeleteTransaction).setOnClickListener(view -> confirmDelete(() -> model.change(FinanceAction.deleteTransaction(id), true)));
    }
    @Override protected void render() {
        if (book == null) return;
        Transaction value = book.transaction(id);
        if (value == null) { if (!model.busy()) finish(); return; }
        boolean transfer = value.type == TransactionType.TRANSFER, voided = "VOIDED".equals(value.status);
        String zone = book.timeZone();
        ((MaterialToolbar) findViewById(R.id.toolbarTransactionDetail)).setTitle(transfer ? R.string.transfer_detail_title : R.string.transaction_detail_title);
        text(R.id.tvDetailTypeBadge, getString(FinanceUi.typeName(value.type)) + (voided ? " · " + getString(R.string.transaction_voided_status) : ""));
        // A VOIDED record is read-only audit history (contract section 5).
        visible(R.id.btnEditTransaction, !voided); visible(R.id.btnDeleteTransaction, !voided);
        text(R.id.tvDetailAmount, (value.type == TransactionType.INCOME ? "+" : transfer ? "" : "−") + FinanceUi.money(value.amount));
        ((TextView) findViewById(R.id.tvDetailAmount)).setTextColor(getColor(value.type == TransactionType.INCOME ? R.color.amount_income
                : transfer ? R.color.amount_transfer : R.color.amount_expense));
        text(R.id.tvDetailDateTime, FinanceUi.date(value.occurredAt, zone)); text(R.id.tvDetailTime, FinanceUi.date(value.occurredAt, zone));
        Category category = book.category(value.categoryId);
        text(R.id.tvDetailCategory, category == null ? "" : category.name + (category.archivedAt == null ? "" : getString(R.string.wallet_archived_suffix)));
        text(R.id.tvDetailWallet, walletName(value.walletId));
        text(R.id.tvDetailStore, value.payee); text(R.id.tvDetailNote, value.note);
        visible(R.id.rowCategory, !transfer); visible(R.id.rowWallet, !transfer); visible(R.id.rowStore, !transfer && !value.payee.isEmpty());
        visible(R.id.rowNote, !value.note.isEmpty()); visible(R.id.rowTransferFrom, transfer); visible(R.id.rowTransferTo, transfer); visible(R.id.rowImpact, transfer);
        if (transfer) {
            text(R.id.tvDetailTransferFrom, walletName(value.walletId)); text(R.id.tvDetailTransferTo, walletName(value.destinationWalletId));
            text(R.id.tvDetailImpact, getString(R.string.transfer_impact_value, walletName(value.walletId),
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
        findViewById(R.id.btnEditTransaction).setEnabled(!loading); findViewById(R.id.btnDeleteTransaction).setEnabled(!loading);
    }
}
