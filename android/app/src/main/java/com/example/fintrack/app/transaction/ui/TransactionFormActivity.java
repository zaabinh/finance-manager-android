package com.example.fintrack.app.transaction.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import java.util.UUID;

public final class TransactionFormActivity extends FinanceActivity {
    public static final String EXTRA_ID = "transaction_id";
    public static final String EXTRA_AMOUNT = "receipt_amount", EXTRA_PAYEE = "receipt_payee";
    private String id, category, wallet, destination;
    private long occurredAt = System.currentTimeMillis();
    private FinanceBook.Type type = FinanceBook.Type.EXPENSE;
    private boolean initialized, editing;
    @Override protected int layout() { return R.layout.activity_transaction_form; }
    @Override protected void bind(Bundle saved) {
        toolbar(R.id.toolbarTransactionForm);
        editing = getIntent().hasExtra(EXTRA_ID);
        id = editing ? getIntent().getStringExtra(EXTRA_ID) : UUID.randomUUID().toString();
        if (saved != null) {
            initialized = saved.getBoolean("initialized"); id = saved.getString("id"); type = FinanceBook.Type.valueOf(saved.getString("type"));
            category = saved.getString("category"); wallet = saved.getString("wallet"); destination = saved.getString("destination");
            occurredAt = saved.getLong("date");
        }
        findViewById(R.id.btnTypeExpense).setOnClickListener(view -> selectType(FinanceBook.Type.EXPENSE));
        findViewById(R.id.btnTypeIncome).setOnClickListener(view -> selectType(FinanceBook.Type.INCOME));
        findViewById(R.id.btnTypeTransfer).setOnClickListener(view -> selectType(FinanceBook.Type.TRANSFER));
        findViewById(R.id.fieldCategory).setOnClickListener(view -> FinanceUi.category(this, type, value -> { category = value; render(); }));
        findViewById(R.id.fieldWallet).setOnClickListener(view -> chooseWallet(false));
        findViewById(R.id.fieldSourceWallet).setOnClickListener(view -> chooseWallet(false));
        findViewById(R.id.fieldDestinationWallet).setOnClickListener(view -> chooseWallet(true));
        findViewById(R.id.fieldDate).setOnClickListener(view -> FinanceUi.dateTime(this, occurredAt, value -> { occurredAt = value; render(); }));
        findViewById(R.id.btnSaveTransaction).setOnClickListener(view -> save());
        if (saved == null && !editing && getIntent().hasExtra(EXTRA_AMOUNT)) {
            long amount = getIntent().getLongExtra(EXTRA_AMOUNT, 0);
            if (amount > 0) text(R.id.etAmount, Long.toString(amount));
            text(R.id.etTransactionPayee, getIntent().getStringExtra(EXTRA_PAYEE));
        }
    }
    private void selectType(FinanceBook.Type value) {
        if (model.busy()) return;
        if (type != value) category = null;
        type = value; render();
    }
    private void chooseWallet(boolean target) {
        FinanceUi.wallet(this, value -> { if (target) destination = value; else wallet = value; render(); });
    }
    @Override protected void render() {
        if (book == null) return;
        if (!initialized) {
            if (editing) {
                FinanceBook.Transaction existing = book.transaction(id);
                if (existing == null) { Toast.makeText(this, R.string.finance_not_found, Toast.LENGTH_LONG).show(); finish(); return; }
                type = existing.type; category = existing.categoryId; wallet = existing.walletId; destination = existing.destinationWalletId;
                occurredAt = existing.occurredAt;
                text(R.id.etAmount, Long.toString(existing.amount)); text(R.id.etTransactionNote, existing.note); text(R.id.etTransactionPayee, existing.payee);
            }
            initialized = true;
        }
        if (wallet == null && !book.state().wallets.isEmpty()) wallet = book.state().wallets.get(0).id;
        boolean transfer = type == FinanceBook.Type.TRANSFER;
        ((MaterialToolbar) findViewById(R.id.toolbarTransactionForm)).setTitle(editing ? R.string.transaction_edit_title
                : transfer ? R.string.transfer_add_title : R.string.transaction_add_title);
        ((MaterialButton) findViewById(R.id.btnTypeExpense)).setChecked(type == FinanceBook.Type.EXPENSE);
        ((MaterialButton) findViewById(R.id.btnTypeIncome)).setChecked(type == FinanceBook.Type.INCOME);
        ((MaterialButton) findViewById(R.id.btnTypeTransfer)).setChecked(transfer);
        visible(R.id.groupExpenseIncome, !transfer); visible(R.id.groupTransfer, transfer); visible(R.id.tvAmountSign, !transfer);
        text(R.id.tvAmountSign, type == FinanceBook.Type.INCOME ? "+" : "−");
        ((TextView) findViewById(R.id.tvAmountSign)).setTextColor(getColor(type == FinanceBook.Type.INCOME ? R.color.amount_income : R.color.amount_expense));
        text(R.id.tvAmountLabel, getString(transfer ? R.string.transfer_amount_label : R.string.transaction_amount_label));
        text(R.id.btnSaveTransaction, getString(editing ? R.string.btn_save_changes : transfer ? R.string.transfer_save : R.string.transaction_save));
        text(R.id.tvCategoryValue, book.category(category) == null ? "" : book.category(category).name);
        text(R.id.tvWalletValue, book.wallet(wallet) == null ? "" : book.wallet(wallet).name);
        text(R.id.tvSourceWalletValue, book.wallet(wallet) == null ? "" : book.wallet(wallet).name);
        text(R.id.tvDestinationWalletValue, book.wallet(destination) == null ? "" : book.wallet(destination).name);
        text(R.id.tvDateValue, FinanceUi.date(occurredAt));
        boolean same = transfer && wallet != null && wallet.equals(destination);
        visible(R.id.feedbackSameWallet, same); visible(R.id.feedbackTransferInfo, !same);
    }
    private void save() {
        if (book == null || model.busy()) return;
        long amount;
        try { amount = FinanceBook.parseAmount(((EditText) findViewById(R.id.etAmount)).getText().toString()); }
        catch (FinanceBook.Failure error) {
            text(R.id.tvAmountError, getString(R.string.finance_amount_error)); visible(R.id.tvAmountError, true);
            findViewById(R.id.etAmount).requestFocus(); return;
        }
        visible(R.id.tvAmountError, false);
        String note = ((EditText) findViewById(R.id.etTransactionNote)).getText().toString();
        String payee = ((EditText) findViewById(R.id.etTransactionPayee)).getText().toString();
        FinanceBook.Transaction value = new FinanceBook.Transaction(id, type, amount, category, wallet, destination, occurredAt, note, payee);
        model.change(FinanceAction.transaction(value, editing), true);
    }
    @Override protected void loading(boolean loading) { findViewById(R.id.btnSaveTransaction).setEnabled(!loading); }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putString("id", id); out.putString("type", type.name()); out.putString("category", category);
        out.putString("wallet", wallet); out.putString("destination", destination); out.putLong("date", occurredAt); out.putBoolean("initialized", initialized);
    }
}
