package com.example.fintrack.app.transaction.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.data.FinanceRepository;
import com.example.fintrack.app.finance.ui.FinanceViewModel;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.transaction.domain.TransactionPrefill;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import java.util.UUID;

public final class TransactionFormActivity extends FinanceActivity {
    public static final String EXTRA_ID = "transaction_id";
    public static final String EXTRA_AMOUNT = "receipt_amount", EXTRA_PAYEE = "receipt_payee";
    /** Optional prefill for a new transaction (spec 2026-11-09 §3); it only fills the form, the user still saves. */
    public static final String EXTRA_PREFILL_TYPE = "prefillType";
    public static final String EXTRA_PREFILL_WALLET_ID = "prefillWalletId";
    public static final String EXTRA_PREFILL_DESTINATION_WALLET_ID = "prefillDestinationWalletId";
    public static final String EXTRA_PREFILL_AMOUNT_MINOR = "prefillAmountMinor";
    private String id, requestId, category, wallet, destination;
    private TransactionPrefill prefill;
    private long occurredAt = System.currentTimeMillis();
    private FinanceBook.Type type = FinanceBook.Type.EXPENSE;
    private boolean initialized, editing;
    @Override protected int layout() { return R.layout.activity_transaction_form; }
    // Editing reads the record by ID (it is not part of the reference snapshot in the real flavor).
    @Override protected FinanceRepository.Scope readScope() {
        return getIntent().hasExtra(EXTRA_ID) ? FinanceRepository.Scope.TRANSACTION_DETAIL : FinanceRepository.Scope.REFERENCES;
    }
    @Override protected String detailId() { return getIntent().getStringExtra(EXTRA_ID); }
    @Override protected void bind(Bundle saved) {
        toolbar(R.id.toolbarTransactionForm);
        editing = getIntent().hasExtra(EXTRA_ID);
        id = editing ? getIntent().getStringExtra(EXTRA_ID) : UUID.randomUUID().toString();
        // Idempotency key: survives rotation/process death so a timeout retry replays instead of double-posting.
        requestId = UUID.randomUUID().toString();
        if (saved != null) {
            initialized = saved.getBoolean("initialized"); id = saved.getString("id"); requestId = saved.getString("requestId", requestId);
            type = FinanceBook.Type.valueOf(saved.getString("type"));
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
        // Wallet IDs are checked against the loaded references in render(); after rotation the form state wins.
        if (saved == null && !editing) prefill = TransactionPrefill.parse(getIntent().getStringExtra(EXTRA_PREFILL_TYPE),
                getIntent().getStringExtra(EXTRA_PREFILL_WALLET_ID), getIntent().getStringExtra(EXTRA_PREFILL_DESTINATION_WALLET_ID),
                getIntent().hasExtra(EXTRA_PREFILL_AMOUNT_MINOR) ? getIntent().getLongExtra(EXTRA_PREFILL_AMOUNT_MINOR, 0) : null);
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
                if (existing == null) { if (model.busy()) return; Toast.makeText(this, R.string.finance_not_found, Toast.LENGTH_LONG).show(); finish(); return; }
                if (!"POSTED".equals(existing.status)) {
                    Toast.makeText(this, R.string.finance_transaction_voided, Toast.LENGTH_LONG).show(); finish(); return;
                }
                type = existing.type; category = existing.categoryId; wallet = existing.walletId; destination = existing.destinationWalletId;
                occurredAt = existing.occurredAt;
                text(R.id.etAmount, Long.toString(existing.amount)); text(R.id.etTransactionNote, existing.note); text(R.id.etTransactionPayee, existing.payee);
            } else if (prefill != null) {
                TransactionPrefill resolved = prefill.resolve(book);
                type = resolved.type; wallet = resolved.walletId; destination = resolved.destinationWalletId;
                if (resolved.amountMinor != null) text(R.id.etAmount, Long.toString(resolved.amountMinor));
                prefill = null;
            }
            initialized = true;
        }
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
        FinanceBook.Category selectedCategory = book.category(category);
        FinanceBook.Wallet selectedWallet = book.wallet(wallet);
        text(R.id.tvCategoryValue, selectedCategory == null ? "" : selectedCategory.name
                + (selectedCategory.archivedAt == null ? "" : getString(R.string.wallet_archived_suffix)));
        text(R.id.tvWalletValue, selectedWallet == null ? "" : selectedWallet.name
                + (selectedWallet.archivedAt == null ? "" : getString(R.string.wallet_archived_suffix)));
        text(R.id.tvSourceWalletValue, walletName(wallet));
        text(R.id.tvDestinationWalletValue, walletName(destination));
        text(R.id.tvDateValue, FinanceUi.date(occurredAt));
        boolean same = transfer && wallet != null && wallet.equals(destination);
        visible(R.id.feedbackSameWallet, same); visible(R.id.feedbackTransferInfo, !same);
    }
    private void save() {
        if (book == null || model.busy()) return;
        boolean transfer = type == FinanceBook.Type.TRANSFER;
        if (!transfer && (category == null || category.isEmpty())) {
            Toast.makeText(this, R.string.error_required_category, Toast.LENGTH_SHORT).show();
            return;
        }
        if (wallet == null || wallet.isEmpty() || (transfer && (destination == null || destination.isEmpty()))) {
            Toast.makeText(this, transfer ? R.string.transfer_required_wallets : R.string.error_required_wallet, Toast.LENGTH_SHORT).show();
            return;
        }
        if (transfer && wallet.equals(destination)) {
            Toast.makeText(this, R.string.error_same_wallet_transfer, Toast.LENGTH_SHORT).show();
            return;
        }
        // Contract: POST/PUT need active wallets (both for a transfer) and, for income/expense, an active category of the same type.
        FinanceBook.Wallet selectedWallet = book.wallet(wallet), selectedDestination = transfer ? book.wallet(destination) : null;
        if (selectedWallet == null || selectedWallet.archivedAt != null
                || (transfer && (selectedDestination == null || selectedDestination.archivedAt != null))) {
            Toast.makeText(this, R.string.transaction_wallet_archived, Toast.LENGTH_LONG).show(); return;
        }
        if (!transfer) {
            FinanceBook.Category selectedCategory = book.category(category);
            if (selectedCategory == null || selectedCategory.archivedAt != null || selectedCategory.type != type) {
                Toast.makeText(this, R.string.transaction_category_archived, Toast.LENGTH_LONG).show(); return;
            }
        }
        long amount;
        try { amount = FinanceBook.parseAmount(((EditText) findViewById(R.id.etAmount)).getText().toString()); }
        catch (FinanceBook.Failure error) {
            text(R.id.tvAmountError, getString(R.string.finance_amount_error)); visible(R.id.tvAmountError, true);
            findViewById(R.id.etAmount).requestFocus(); return;
        }
        visible(R.id.tvAmountError, false);
        String note = ((EditText) findViewById(R.id.etTransactionNote)).getText().toString();
        String payee = ((EditText) findViewById(R.id.etTransactionPayee)).getText().toString();
        FinanceBook.Transaction value = draft(id, type, amount, category, wallet, destination, occurredAt, note, payee);
        value.clientRequestId = requestId;
        model.change(FinanceAction.transaction(value, editing), true);
    }
    /** Form → domain value: a transfer never carries a category, income/expense never a destination. */
    static FinanceBook.Transaction draft(String id, FinanceBook.Type type, long amount, String category, String wallet,
            String destination, long occurredAt, String note, String payee) {
        boolean transfer = type == FinanceBook.Type.TRANSFER;
        return new FinanceBook.Transaction(id, type, amount, transfer ? null : category, wallet, transfer ? destination : null,
                occurredAt, note, payee);
    }
    private String walletName(String walletId) {
        FinanceBook.Wallet value = book.wallet(walletId);
        return value == null ? "" : value.name + (value.archivedAt == null ? "" : getString(R.string.wallet_archived_suffix));
    }
    @Override protected CharSequence failure(FinanceViewModel.Command command) {
        // The server answered definitively: the key is spent, so a fresh save must not replay the voided record.
        if (!editing && command.error == FinanceBook.Problem.VOIDED) requestId = UUID.randomUUID().toString();
        if (command.failure != null && command.failure.fields.contains("amountMinor")) {
            text(R.id.tvAmountError, getString(R.string.finance_amount_error)); visible(R.id.tvAmountError, true);
        }
        return message(this, command);
    }
    /** Vietnamese text for transaction command failures; never shows the (English) server message. */
    static CharSequence message(android.content.Context context, FinanceViewModel.Command command) {
        FinanceBook.Failure failure = command.failure;
        String code = failure == null ? null : failure.code;
        if ("TRANSACTION_REPLAY_VOIDED".equals(code)) return context.getString(R.string.transaction_replay_voided);
        if ("TRANSACTION_REQUEST_CONFLICT".equals(code)) return context.getString(R.string.transaction_request_conflict);
        if ("TRANSACTION_TYPE_UNSUPPORTED".equals(code)) return context.getString(R.string.transaction_type_unsupported);
        if ("WALLET_ARCHIVED".equals(code)) return context.getString(R.string.transaction_wallet_archived);
        if ("CATEGORY_ARCHIVED".equals(code)) return context.getString(R.string.transaction_category_archived);
        java.util.List<String> labels = new java.util.ArrayList<>();
        if (failure != null) for (String field : failure.fields) {
            int label = "amountMinor".equals(field) ? R.string.transaction_field_amount : "categoryId".equals(field) ? R.string.transaction_field_category
                    : "walletId".equals(field) ? R.string.transaction_field_wallet
                    : "destinationWalletId".equals(field) ? R.string.transaction_field_destination_wallet : "occurredAt".equals(field) ? R.string.transaction_field_date
                    : "note".equals(field) ? R.string.transaction_field_note : "payee".equals(field) ? R.string.transaction_field_payee
                    : "transactionType".equals(field) ? R.string.transaction_field_type : 0;
            if (label != 0 && !labels.contains(context.getString(label))) labels.add(context.getString(label));
        }
        if (!labels.isEmpty()) return context.getString(R.string.transaction_field_errors, android.text.TextUtils.join(", ", labels));
        return context.getString(FinanceUi.error(command.error));
    }
    @Override protected void loading(boolean loading) { findViewById(R.id.btnSaveTransaction).setEnabled(!loading); }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putString("id", id); out.putString("requestId", requestId); out.putString("type", type.name()); out.putString("category", category);
        out.putString("wallet", wallet); out.putString("destination", destination); out.putLong("date", occurredAt); out.putBoolean("initialized", initialized);
    }
}
