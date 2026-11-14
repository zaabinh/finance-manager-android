package com.example.fintrack.app.transaction.ui;

import com.example.fintrack.app.finance.domain.Category;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.FinanceFailure;
import com.example.fintrack.app.finance.domain.Transaction;
import com.example.fintrack.app.finance.domain.TransactionType;
import com.example.fintrack.app.finance.domain.Wallet;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import com.example.fintrack.R;
import com.example.fintrack.app.FintrackApplication;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.transaction.viewmodel.TransactionReceiptViewModel;
import com.example.fintrack.databinding.ActivityTransactionReviewBinding;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Màn hình xem lại kết quả trích xuất thông tin hóa đơn từ OCR (số tiền, cửa hàng, ngày tháng, danh mục).
 */
public final class TransactionReviewActivity extends FinanceActivity {
    private static final String STATE_ID = "id", STATE_CATEGORY = "category", STATE_WALLET = "wallet",
            STATE_PAYEE = "payee", STATE_DATE = "date", STATE_APPLIED = "applied";
    /** Receipt image URIs (strings); one screen reviews the image at EXTRA_INDEX, then opens the next. */
    public static final String EXTRA_IMAGES = "images", EXTRA_INDEX = "index";
    private ArrayList<String> images;
    private int index;
    private String id = UUID.randomUUID().toString(), category, wallet, payee = "";
    private long date = System.currentTimeMillis();
    private boolean applied, reading = true, unreadable;

    private ActivityTransactionReviewBinding binding;
    @Override protected View contentView() { binding = ActivityTransactionReviewBinding.inflate(getLayoutInflater()); return binding.getRoot(); }

    @Override protected void bind(Bundle saved) {
        binding.toolbarReceiptReview.setNavigationOnClickListener(view -> finish());
        // The suggestion chips live in an include without an android:id, so ViewBinding does not expose them.
        com.example.fintrack.app.core.ui.MoneyInput.attach(binding.amountInput.etAmount, binding.getRoot().<ChipGroup>findViewById(R.id.chipGroupAmountSuggestions));
        images = getIntent().getStringArrayListExtra(EXTRA_IMAGES);
        index = getIntent().getIntExtra(EXTRA_INDEX, 0);
        if (images == null || index < 0 || index >= images.size()) { finish(); return; }

        if (saved != null) {
            id = saved.getString(STATE_ID);
            category = saved.getString(STATE_CATEGORY);
            wallet = saved.getString(STATE_WALLET);
            payee = saved.getString(STATE_PAYEE, "");
            date = saved.getLong(STATE_DATE);
            applied = saved.getBoolean(STATE_APPLIED);
        }

        MaterialToolbar toolbar = binding.toolbarReceiptReview;
        toolbar.setTitle(getString(R.string.receipt_review_title_batch, index + 1, images.size()));

        // Menu xem ảnh gốc hóa đơn
        toolbar.getMenu().add(R.string.finance_view_receipt).setOnMenuItemClickListener(item -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(images.get(index)), "image/*")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            } catch (android.content.ActivityNotFoundException error) {
                android.widget.Toast.makeText(this, R.string.finance_camera_error, android.widget.Toast.LENGTH_LONG).show();
            }
            return true;
        });

        binding.amountInput.tvAmountLabel.setText(getString(R.string.receipt_amount_label));
        binding.amountInput.tvAmountSign.setVisibility(View.GONE);
        binding.tvReviewFeedback.setText(getString(R.string.finance_receipt_check));

        // Thiết lập sự kiện chọn danh mục, ví, thời gian và cửa hàng
        binding.fieldCategory.setOnClickListener(view -> FinanceUi.category(this, TransactionType.EXPENSE, value -> { category = value; render(); }));
        binding.fieldWallet.setOnClickListener(view -> FinanceUi.wallet(this, value -> { wallet = value; render(); }));
        binding.fieldDate.setOnClickListener(view -> FinanceUi.dateTime(this, date, value -> { date = value; render(); }));
        binding.fieldStore.setOnClickListener(view -> {
            EditText edit = new EditText(this); edit.setText(payee); edit.setHint(R.string.transaction_store_label);
            new MaterialAlertDialogBuilder(this).setTitle(R.string.transaction_store_label).setView(edit)
                    .setNegativeButton(R.string.btn_cancel, null).setPositiveButton(R.string.picker_confirm, (dialog, which) -> { payee = edit.getText().toString(); render(); }).show();
        });

        binding.btnConfirmReceipt.setOnClickListener(view -> save());
        binding.btnAddReceipt.setOnClickListener(view -> completed());
        binding.btnAddReceipt.setText(getString(R.string.finance_skip_receipt));
        binding.btnRescan.setOnClickListener(view -> finish());
        binding.stateUnreadable.btnStatePrimary.setOnClickListener(view -> finish());
        binding.btnManualEntry.setOnClickListener(view -> { startActivity(new Intent(this, TransactionFormActivity.class)); finish(); });

        // Đọc dữ liệu từ ảnh bằng OCR ViewModel
        TransactionReceiptViewModel receipt = new ViewModelProvider(this, FintrackApplication.container(this).viewModelFactory()).get(TransactionReceiptViewModel.class);
        receipt.draft().observe(this, resource -> {
            reading = resource.getStatus() == Resource.Status.LOADING;
            unreadable = resource.getStatus() == Resource.Status.ERROR;
            if (resource.getData() != null && !applied) {
                payee = resource.getData().payee;
                if (resource.getData().amount > 0) binding.amountInput.etAmount.setText(Long.toString(resource.getData().amount));
                applied = true;
            }
            render();
            loading(model.saveLoading());
        });
        receipt.read(Uri.parse(images.get(index)));
    }

    @Override protected void render() {
        FinanceUi.visible(binding.groupReviewFields, !unreadable && !reading);
        FinanceUi.visible(binding.stateUnreadable.getRoot(), unreadable);
        FinanceUi.visible(binding.feedbackReview, !unreadable);
        FinanceUi.visible(binding.btnManualEntry, unreadable);
        FinanceUi.visible(binding.btnConfirmReceipt, !unreadable && !reading);
        binding.tvReviewFeedback.setText(getString(reading ? R.string.receipt_import_reading : R.string.finance_receipt_check));

        if (unreadable) {
            binding.stateUnreadable.tvStateTitle.setText(getString(R.string.receipt_unreadable_title));
            binding.stateUnreadable.tvStateBody.setText(getString(R.string.receipt_unreadable_body));
            binding.stateUnreadable.btnStatePrimary.setText(getString(R.string.receipt_retake));
        }

        if (book == null) return;
        if (wallet == null && !book.wallets().isEmpty()) wallet = book.wallets().get(0).id;

        binding.tvStoreValue.setText(payee);
        binding.tvDateValue.setText(FinanceUi.date(date));
        Category catObj = book.category(category);
        binding.tvCategoryValue.setText(catObj == null ? "" : catObj.name);
        Wallet walletObj = book.wallet(wallet);
        binding.tvWalletValue.setText(walletObj == null ? "" : walletObj.name);
    }

    // Lưu giao dịch được trích xuất từ hóa đơn vào cơ sở dữ liệu
    private void save() {
        if (reading || book == null || model.saveLoading()) return;
        long amount;
        try { amount = FinanceBook.parseAmount(binding.amountInput.etAmount.getText().toString()); }
        catch (FinanceFailure error) {
            binding.amountInput.tvAmountError.setVisibility(View.VISIBLE); binding.amountInput.tvAmountError.setText(getString(R.string.finance_amount_error)); return;
        }
        binding.amountInput.tvAmountError.setVisibility(View.GONE);

        Transaction value = new Transaction(id, TransactionType.EXPENSE, amount, category, wallet, null, date, "", payee);
        model.change(FinanceAction.transaction(value, false), true);
    }

    @Override protected void loading(boolean loading) {
        FinanceUi.progress(binding.btnConfirmReceipt, loading);
        binding.btnConfirmReceipt.setEnabled(!loading && !reading);
    }

    @Override protected void completed() {
        if (images != null && index + 1 < images.size()) {
            startActivity(new Intent(this, TransactionReviewActivity.class)
                    .putStringArrayListExtra(EXTRA_IMAGES, images).putExtra(EXTRA_INDEX, index + 1));
        }
        finish();
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(STATE_ID, id);
        out.putString(STATE_CATEGORY, category);
        out.putString(STATE_WALLET, wallet);
        out.putString(STATE_PAYEE, payee);
        out.putLong(STATE_DATE, date);
        out.putBoolean(STATE_APPLIED, applied);
    }
}
