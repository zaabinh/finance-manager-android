package com.example.fintrack.app.transaction.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.google.android.material.appbar.MaterialToolbar;

public final class TransactionDetailActivity extends FinanceActivity {
    private String id;
    @Override protected int layout() { return R.layout.activity_transaction_detail; }
    @Override protected void bind(Bundle saved) {
        id = getIntent().getStringExtra(TransactionFormActivity.EXTRA_ID); toolbar(R.id.toolbarTransactionDetail);
        findViewById(R.id.btnEditTransaction).setOnClickListener(view -> startActivity(new Intent(this, TransactionFormActivity.class)
                .putExtra(TransactionFormActivity.EXTRA_ID, id)));
        findViewById(R.id.btnDeleteTransaction).setOnClickListener(view -> confirmDelete(() -> model.change(FinanceAction.deleteTransaction(id), true)));
    }
    @Override protected void render() {
        if (book == null) return;
        FinanceBook.Transaction value = book.transaction(id);
        if (value == null) { if (!model.busy()) finish(); return; }
        boolean transfer = value.type == FinanceBook.Type.TRANSFER;
        ((MaterialToolbar) findViewById(R.id.toolbarTransactionDetail)).setTitle(transfer ? R.string.transfer_detail_title : R.string.transaction_detail_title);
        text(R.id.tvDetailTypeBadge, getString(FinanceUi.typeName(value.type)));
        text(R.id.tvDetailAmount, (value.type == FinanceBook.Type.INCOME ? "+" : transfer ? "" : "−") + FinanceUi.money(value.amount));
        ((TextView) findViewById(R.id.tvDetailAmount)).setTextColor(getColor(value.type == FinanceBook.Type.INCOME ? R.color.amount_income
                : transfer ? R.color.amount_transfer : R.color.amount_expense));
        text(R.id.tvDetailDateTime, FinanceUi.date(value.occurredAt)); text(R.id.tvDetailTime, FinanceUi.date(value.occurredAt));
        text(R.id.tvDetailCategory, book.category(value.categoryId) == null ? "" : book.category(value.categoryId).name);
        text(R.id.tvDetailWallet, book.wallet(value.walletId).name);
        text(R.id.tvDetailStore, value.payee); text(R.id.tvDetailNote, value.note);
        visible(R.id.rowCategory, !transfer); visible(R.id.rowWallet, !transfer); visible(R.id.rowStore, !transfer && !value.payee.isEmpty());
        visible(R.id.rowNote, !value.note.isEmpty()); visible(R.id.rowTransferFrom, transfer); visible(R.id.rowTransferTo, transfer); visible(R.id.rowImpact, transfer);
        if (transfer) {
            text(R.id.tvDetailTransferFrom, book.wallet(value.walletId).name); text(R.id.tvDetailTransferTo, book.wallet(value.destinationWalletId).name);
            text(R.id.tvDetailImpact, getString(R.string.transfer_info_note));
        }
    }
    @Override protected void loading(boolean loading) {
        findViewById(R.id.btnEditTransaction).setEnabled(!loading); findViewById(R.id.btnDeleteTransaction).setEnabled(!loading);
    }
}
