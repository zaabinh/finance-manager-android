package com.example.fintrack.app.transaction.ui;

import com.example.fintrack.app.finance.domain.Category;
import com.example.fintrack.app.finance.domain.FinanceFailure;
import com.example.fintrack.app.finance.domain.Transaction;
import com.example.fintrack.app.finance.domain.TransactionType;
import com.example.fintrack.app.finance.domain.Wallet;
import com.example.fintrack.app.finance.domain.WalletType;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.data.FinanceRepository;
import com.example.fintrack.app.finance.ui.FinanceViewModel;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.transaction.domain.TransactionFormValidator;
import com.example.fintrack.app.transaction.domain.TransactionPrefill;
import com.example.fintrack.databinding.ActivityTransactionFormBinding;
import com.google.android.material.chip.ChipGroup;
import java.util.UUID;

public final class TransactionFormActivity extends FinanceActivity {
    private static final String STATE_INITIALIZED = "initialized", STATE_ID = "id", STATE_REQUEST_ID = "requestId",
            STATE_TYPE = "type", STATE_CATEGORY = "category", STATE_WALLET = "wallet",
            STATE_DESTINATION = "destination", STATE_DATE = "date", STATE_AWAITING_WALLET = "awaitingWallet",
            STATE_AWAITING_DESTINATION = "awaitingDestination", STATE_AWAITING_CATEGORY = "awaitingCategory";
    public static final String EXTRA_ID = "transaction_id";
    public static final String EXTRA_AMOUNT = "receipt_amount", EXTRA_PAYEE = "receipt_payee", EXTRA_NOTE = "transaction_note";
    /** Optional prefill for a new transaction (spec 2026-11-09 §3); it only fills the form, the user still saves. */
    public static final String EXTRA_PREFILL_TYPE = "prefillType";
    public static final String EXTRA_PREFILL_WALLET_ID = "prefillWalletId";
    public static final String EXTRA_PREFILL_DESTINATION_WALLET_ID = "prefillDestinationWalletId";
    public static final String EXTRA_PREFILL_AMOUNT_MINOR = "prefillAmountMinor";
    public static final String EXTRA_PREFILL_CATEGORY_ID = "prefillCategoryId";
    private String id, requestId, category, wallet, destination;
    private TransactionPrefill prefill;
    private long occurredAt = System.currentTimeMillis();
    private TransactionType type = TransactionType.EXPENSE;
    private boolean initialized, editing;
    private boolean awaitingWallet, awaitingDestination, awaitingCategory;
    private com.example.fintrack.app.wallet.ui.WalletSheet walletSheet;
    private com.example.fintrack.app.category.ui.CategorySheet categorySheet;
    private ActivityTransactionFormBinding binding;
    @Override protected View contentView() { binding = ActivityTransactionFormBinding.inflate(getLayoutInflater()); return binding.getRoot(); }
    // Editing reads the record by ID (it is not part of the reference snapshot in the real flavor).
    @Override protected FinanceRepository.Scope readScope() {
        return getIntent().hasExtra(EXTRA_ID) ? FinanceRepository.Scope.TRANSACTION_DETAIL : FinanceRepository.Scope.REFERENCES;
    }
    @Override protected String detailId() { return getIntent().getStringExtra(EXTRA_ID); }
    @Override protected void bind(Bundle saved) {
        binding.toolbarTransactionForm.setNavigationOnClickListener(view -> finish());
        // The suggestion chips live in an include without an android:id, so ViewBinding does not expose them.
        com.example.fintrack.app.core.ui.MoneyInput.attach(binding.amountInput.etAmount, binding.getRoot().<ChipGroup>findViewById(R.id.chipGroupAmountSuggestions));
        editing = getIntent().hasExtra(EXTRA_ID);
        id = editing ? getIntent().getStringExtra(EXTRA_ID) : UUID.randomUUID().toString();
        // Idempotency key: survives rotation/process death so a timeout retry replays instead of double-posting.
        requestId = UUID.randomUUID().toString();
        if (saved != null) {
            initialized = saved.getBoolean(STATE_INITIALIZED); id = saved.getString(STATE_ID); requestId = saved.getString(STATE_REQUEST_ID, requestId);
            type = TransactionType.valueOf(saved.getString(STATE_TYPE));
            category = saved.getString(STATE_CATEGORY); wallet = saved.getString(STATE_WALLET); destination = saved.getString(STATE_DESTINATION);
            occurredAt = saved.getLong(STATE_DATE);
            awaitingWallet = saved.getBoolean(STATE_AWAITING_WALLET);
            awaitingDestination = saved.getBoolean(STATE_AWAITING_DESTINATION);
            awaitingCategory = saved.getBoolean(STATE_AWAITING_CATEGORY);
        }
        binding.segmentedTransactionType.btnTypeExpense.setOnClickListener(view -> selectType(TransactionType.EXPENSE));
        binding.segmentedTransactionType.btnTypeIncome.setOnClickListener(view -> selectType(TransactionType.INCOME));
        binding.segmentedTransactionType.btnTypeTransfer.setOnClickListener(view -> selectType(TransactionType.TRANSFER));
        binding.fieldCategory.setOnClickListener(view -> chooseCategory());
        binding.fieldWallet.setOnClickListener(view -> chooseWallet(false));
        binding.fieldSourceWallet.setOnClickListener(view -> chooseWallet(false));
        binding.fieldDestinationWallet.setOnClickListener(view -> chooseWallet(true));
        binding.fieldDate.setOnClickListener(view -> FinanceUi.dateTime(this, occurredAt, value -> { occurredAt = value; render(); }));
        binding.btnSaveTransaction.setOnClickListener(view -> save());
        if (saved == null && !editing) {
            if (getIntent().hasExtra(EXTRA_AMOUNT)) {
                long amount = getIntent().getLongExtra(EXTRA_AMOUNT, 0);
                if (amount > 0) binding.amountInput.etAmount.setText(Long.toString(amount));
                binding.etTransactionPayee.setText(getIntent().getStringExtra(EXTRA_PAYEE));
            }
            if (getIntent().hasExtra(EXTRA_NOTE)) {
                binding.etTransactionNote.setText(getIntent().getStringExtra(EXTRA_NOTE));
            }
        }
        // Wallet IDs are checked against the loaded references in render(); after rotation the form state wins.
        // Intent extras survive rotation, so re-parse until the form has loaded once.
        if (!editing && !initialized) prefill = TransactionPrefill.parse(getIntent().getStringExtra(EXTRA_PREFILL_TYPE),
                getIntent().getStringExtra(EXTRA_PREFILL_WALLET_ID), getIntent().getStringExtra(EXTRA_PREFILL_DESTINATION_WALLET_ID),
                getIntent().hasExtra(EXTRA_PREFILL_AMOUNT_MINOR) ? getIntent().getLongExtra(EXTRA_PREFILL_AMOUNT_MINOR, 0) : null,
                getIntent().getStringExtra(EXTRA_PREFILL_CATEGORY_ID));
    }
    private void selectType(TransactionType value) {
        if (model.saveLoading()) return;
        if (type != value) category = null;
        type = value; render();
    }
    private void chooseWallet(boolean target) {
        if (model.saveLoading()) return;
        if (book == null) { Toast.makeText(this, R.string.finance_wallet_empty, Toast.LENGTH_SHORT).show(); return; }
        // A transfer needs two different wallets, so the other side is not offered.
        String other = type == TransactionType.TRANSFER ? (target ? wallet : destination) : null;
        com.example.fintrack.app.core.ui.PickerSheet.show(this, R.string.picker_wallet_title,
                com.example.fintrack.app.core.ui.PickerSheet.wallets(book.wallets(), other), target ? destination : wallet,
                R.string.picker_add_wallet, () -> createWallet(target), value -> { if (target) destination = value; else wallet = value; render(); });
    }
    private void chooseCategory() {
        if (model.saveLoading()) return;
        if (book == null) { Toast.makeText(this, R.string.finance_category_empty, Toast.LENGTH_SHORT).show(); return; }
        com.example.fintrack.app.core.ui.PickerSheet.show(this, R.string.picker_category_title,
                com.example.fintrack.app.core.ui.PickerSheet.categories(book.categories(), type), category,
                R.string.picker_add_category, this::createCategory, value -> { category = value; render(); });
    }
    private void createWallet(boolean target) {
        walletSheet = com.example.fintrack.app.wallet.ui.WalletSheet.show(this, (sheet, value) -> {
            if (book == null || model.saveLoading()) return;
            awaitingWallet = !target; awaitingDestination = target; awaitingCategory = false;
            model.change(FinanceAction.wallet(value.name, value.openingBalance, WalletType.valueOf(value.walletType),
                    value.includeInAssets, value.sortOrder), false);
        });
    }
    private void createCategory() {
        categorySheet = com.example.fintrack.app.category.ui.CategorySheet.show(this, type, (sheet, name, categoryType, icon, colorHex) -> {
            if (book == null || model.saveLoading()) return;
            // The new category must match the transaction type, so the form follows the sheet's type choice.
            if (categoryType != type && type != TransactionType.TRANSFER) { category = null; type = categoryType; }
            awaitingCategory = true; awaitingWallet = awaitingDestination = false;
            model.change(FinanceAction.category(name, categoryType, icon, colorHex, 0), false);
        });
    }
    @Override protected void onCommand(FinanceViewModel.Command command) {
        if (command.error != null) {
            awaitingWallet = awaitingDestination = awaitingCategory = false;
            return;
        }
        if (command.receipt == null || command.receipt.createdEntityId == null) return;
        String createdId = command.receipt.createdEntityId;
        if (awaitingWallet || awaitingDestination) {
            if (awaitingDestination) destination = createdId; else wallet = createdId;
            if (walletSheet != null) walletSheet.dismiss();
        } else if (awaitingCategory) {
            category = createdId;
            if (categorySheet != null) categorySheet.dismiss();
        }
        awaitingWallet = awaitingDestination = awaitingCategory = false;
        render();
    }
    @Override protected void render() {
        if (book == null) return;
        if (!initialized) {
            if (editing) {
                Transaction existing = book.transaction(id);
                if (existing == null) { if (model.snapshotLoading()) return; Toast.makeText(this, R.string.finance_not_found, Toast.LENGTH_LONG).show(); finish(); return; }
                if (!"POSTED".equals(existing.status)) {
                    Toast.makeText(this, R.string.finance_transaction_voided, Toast.LENGTH_LONG).show(); finish(); return;
                }
                type = existing.type; category = existing.categoryId; wallet = existing.walletId; destination = existing.destinationWalletId;
                occurredAt = existing.occurredAt;
                binding.amountInput.etAmount.setText(Long.toString(existing.amount)); binding.etTransactionNote.setText(existing.note); binding.etTransactionPayee.setText(existing.payee);
            } else if (prefill != null) {
                TransactionPrefill resolved = prefill.resolve(book);
                type = resolved.type; wallet = resolved.walletId; destination = resolved.destinationWalletId;
                if (resolved.categoryId != null) category = resolved.categoryId;
                if (resolved.amountMinor != null) binding.amountInput.etAmount.setText(Long.toString(resolved.amountMinor));
                prefill = null;
            }
            initialized = true;
        }
        boolean transfer = type == TransactionType.TRANSFER;
        binding.toolbarTransactionForm.setTitle(editing ? R.string.transaction_edit_title
                : transfer ? R.string.transfer_add_title : R.string.transaction_add_title);
        binding.segmentedTransactionType.btnTypeExpense.setChecked(type == TransactionType.EXPENSE);
        binding.segmentedTransactionType.btnTypeIncome.setChecked(type == TransactionType.INCOME);
        binding.segmentedTransactionType.btnTypeTransfer.setChecked(transfer);
        FinanceUi.visible(binding.groupExpenseIncome, !transfer); FinanceUi.visible(binding.groupTransfer, transfer); FinanceUi.visible(binding.amountInput.tvAmountSign, !transfer);
        binding.amountInput.tvAmountSign.setText(type == TransactionType.INCOME ? "+" : "−");
        binding.amountInput.tvAmountSign.setTextColor(getColor(type == TransactionType.INCOME ? R.color.amount_income : R.color.amount_expense));
        binding.amountInput.tvAmountLabel.setText(getString(transfer ? R.string.transfer_amount_label : R.string.transaction_amount_label));
        binding.btnSaveTransaction.setText(getString(editing ? R.string.btn_save_changes : transfer ? R.string.transfer_save : R.string.transaction_save));
        Category selectedCategory = book.category(category);
        Wallet selectedWallet = book.wallet(wallet);
        binding.tvCategoryValue.setText(selectedCategory == null ? "" : selectedCategory.name
                + (selectedCategory.archivedAt == null ? "" : getString(R.string.wallet_archived_suffix)));
        binding.tvWalletValue.setText(selectedWallet == null ? "" : selectedWallet.name
                + (selectedWallet.archivedAt == null ? "" : getString(R.string.wallet_archived_suffix)));
        binding.tvSourceWalletValue.setText(walletName(wallet));
        binding.tvDestinationWalletValue.setText(walletName(destination));
        binding.tvDateValue.setText(FinanceUi.date(occurredAt, book.timeZone()));
        boolean same = transfer && wallet != null && wallet.equals(destination);
        FinanceUi.visible(binding.feedbackSameWallet, same); FinanceUi.visible(binding.feedbackTransferInfo, !same);
    }
    private void save() {
        if (book == null || model.saveLoading()) return;
        TransactionFormValidator.Result result = TransactionFormValidator.validate(book, id, type, category, wallet, destination,
                occurredAt, binding.amountInput.etAmount.getText().toString(), binding.etTransactionNote.getText().toString(),
                binding.etTransactionPayee.getText().toString());
        if (!result.valid()) {
            switch (result.error) {
                case MISSING_CATEGORY: Toast.makeText(this, R.string.error_required_category, Toast.LENGTH_SHORT).show(); break;
                case MISSING_WALLET: Toast.makeText(this, R.string.error_required_wallet, Toast.LENGTH_SHORT).show(); break;
                case MISSING_TRANSFER_WALLETS: Toast.makeText(this, R.string.transfer_required_wallets, Toast.LENGTH_SHORT).show(); break;
                case SAME_WALLET: Toast.makeText(this, R.string.error_same_wallet_transfer, Toast.LENGTH_SHORT).show(); break;
                case WALLET_ARCHIVED: Toast.makeText(this, R.string.transaction_wallet_archived, Toast.LENGTH_LONG).show(); break;
                case CATEGORY_ARCHIVED: Toast.makeText(this, R.string.transaction_category_archived, Toast.LENGTH_LONG).show(); break;
                default:
                    binding.amountInput.tvAmountError.setText(getString(R.string.finance_amount_error));
                    binding.amountInput.tvAmountError.setVisibility(View.VISIBLE);
                    binding.amountInput.etAmount.requestFocus();
            }
            return;
        }
        binding.amountInput.tvAmountError.setVisibility(View.GONE);
        // Same key until the server answers definitively (see failure()), so a timeout retry replays instead of double-posting.
        model.change(FinanceAction.transaction(result.transaction.withClientRequestId(requestId), editing), true);
    }
    private String walletName(String walletId) {
        Wallet value = book.wallet(walletId);
        return value == null ? "" : value.name + (value.archivedAt == null ? "" : getString(R.string.wallet_archived_suffix));
    }
    @Override protected CharSequence failure(FinanceViewModel.Command command) {
        // The server answered definitively: the key is spent, so a fresh save must not replay the voided record.
        if (!editing && command.failure != null && "TRANSACTION_REPLAY_VOIDED".equals(command.failure.code))
            requestId = UUID.randomUUID().toString();
        if (command.failure != null && command.failure.fields.contains("amountMinor")) {
            binding.amountInput.tvAmountError.setText(getString(R.string.finance_amount_error)); binding.amountInput.tvAmountError.setVisibility(View.VISIBLE);
        }
        return message(this, command);
    }
    /** Vietnamese text for transaction command failures; never shows the (English) server message. */
    static CharSequence message(android.content.Context context, FinanceViewModel.Command command) {
        FinanceFailure failure = command.failure;
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
    @Override protected void loading(boolean loading) {
        FinanceUi.progress(binding.btnSaveTransaction, loading);
        boolean creating = model.saveLoading() && (awaitingWallet || awaitingDestination || awaitingCategory);
        if (walletSheet != null && (awaitingWallet || awaitingDestination || !loading)) walletSheet.loading(creating);
        if (categorySheet != null && (awaitingCategory || !loading)) categorySheet.loading(creating);
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putString(STATE_ID, id); out.putString(STATE_REQUEST_ID, requestId); out.putString(STATE_TYPE, type.name()); out.putString(STATE_CATEGORY, category);
        out.putBoolean(STATE_AWAITING_WALLET, awaitingWallet); out.putBoolean(STATE_AWAITING_DESTINATION, awaitingDestination);
        out.putBoolean(STATE_AWAITING_CATEGORY, awaitingCategory);
        out.putString(STATE_WALLET, wallet); out.putString(STATE_DESTINATION, destination); out.putLong(STATE_DATE, occurredAt); out.putBoolean(STATE_INITIALIZED, initialized);
    }
}
