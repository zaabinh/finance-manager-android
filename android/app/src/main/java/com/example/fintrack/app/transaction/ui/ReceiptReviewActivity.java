package com.example.fintrack.app.transaction.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
import androidx.lifecycle.ViewModelProvider;
import com.example.fintrack.R;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.UUID;

public final class ReceiptReviewActivity extends FinanceActivity {
    private ArrayList<String> images;
    private int index;
    private String id = UUID.randomUUID().toString(), category, wallet, payee = "";
    private long date = System.currentTimeMillis();
    private boolean applied, reading = true, unreadable;
    @Override protected int layout() { return R.layout.activity_receipt_review; }
    @Override protected void bind(Bundle saved) {
        toolbar(R.id.toolbarReceiptReview); images = getIntent().getStringArrayListExtra("images"); index = getIntent().getIntExtra("index", 0);
        if (images == null || index < 0 || index >= images.size()) { finish(); return; }
        if (saved != null) {
            id = saved.getString("id"); category = saved.getString("category"); wallet = saved.getString("wallet");
            payee = saved.getString("payee", ""); date = saved.getLong("date"); applied = saved.getBoolean("applied");
        }
        MaterialToolbar toolbar = findViewById(R.id.toolbarReceiptReview);
        toolbar.setTitle(getString(R.string.receipt_review_title_batch, index + 1, images.size()));
        toolbar.getMenu().add(R.string.finance_view_receipt).setOnMenuItemClickListener(item -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(images.get(index)), "image/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); }
            catch (android.content.ActivityNotFoundException error) { android.widget.Toast.makeText(this, R.string.finance_camera_error, android.widget.Toast.LENGTH_LONG).show(); }
            return true;
        });
        text(R.id.tvAmountLabel, getString(R.string.receipt_amount_label)); visible(R.id.tvAmountSign, false);
        text(R.id.tvReviewFeedback, getString(R.string.finance_receipt_check));
        findViewById(R.id.fieldCategory).setOnClickListener(view -> FinanceUi.category(this, FinanceBook.Type.EXPENSE, value -> { category = value; render(); }));
        findViewById(R.id.fieldWallet).setOnClickListener(view -> FinanceUi.wallet(this, value -> { wallet = value; render(); }));
        findViewById(R.id.fieldDate).setOnClickListener(view -> FinanceUi.dateTime(this, date, value -> { date = value; render(); }));
        findViewById(R.id.fieldStore).setOnClickListener(view -> {
            EditText edit = new EditText(this); edit.setText(payee); edit.setHint(R.string.transaction_store_label);
            new MaterialAlertDialogBuilder(this).setTitle(R.string.transaction_store_label).setView(edit)
                    .setNegativeButton(R.string.btn_cancel, null).setPositiveButton(R.string.picker_confirm, (dialog, which) -> { payee = edit.getText().toString(); render(); }).show();
        });
        findViewById(R.id.btnConfirmReceipt).setOnClickListener(view -> save());
        findViewById(R.id.btnAddReceipt).setOnClickListener(view -> completed());
        text(R.id.btnAddReceipt, getString(R.string.finance_skip_receipt));
        findViewById(R.id.btnRescan).setOnClickListener(view -> finish());
        findViewById(R.id.btnStatePrimary).setOnClickListener(view -> finish());
        findViewById(R.id.btnManualEntry).setOnClickListener(view -> { startActivity(new Intent(this, TransactionFormActivity.class)); finish(); });
        ReceiptViewModel receipt = new ViewModelProvider(this).get(ReceiptViewModel.class);
        receipt.draft().observe(this, resource -> {
            reading = resource.getStatus() == Resource.Status.LOADING;
            unreadable = resource.getStatus() == Resource.Status.ERROR;
            if (resource.getData() != null && !applied) {
                payee = resource.getData().payee;
                if (resource.getData().amount > 0) text(R.id.etAmount, Long.toString(resource.getData().amount));
                applied = true;
            }
            render(); loading(model.busy());
        });
        receipt.read(Uri.parse(images.get(index)));
    }
    @Override protected void render() {
        visible(R.id.groupReviewFields, !unreadable && !reading); visible(R.id.stateUnreadable, unreadable);
        visible(R.id.feedbackReview, !unreadable); visible(R.id.btnManualEntry, unreadable);
        visible(R.id.btnConfirmReceipt, !unreadable && !reading);
        text(R.id.tvReviewFeedback, getString(reading ? R.string.receipt_import_reading : R.string.finance_receipt_check));
        if (unreadable) {
            text(R.id.tvStateTitle, getString(R.string.receipt_unreadable_title)); text(R.id.tvStateBody, getString(R.string.receipt_unreadable_body));
            text(R.id.btnStatePrimary, getString(R.string.receipt_retake));
        }
        if (book == null) return;
        if (wallet == null && !book.state().wallets.isEmpty()) wallet = book.state().wallets.get(0).id;
        text(R.id.tvStoreValue, payee); text(R.id.tvDateValue, FinanceUi.date(date));
        text(R.id.tvCategoryValue, book.category(category) == null ? "" : book.category(category).name);
        text(R.id.tvWalletValue, book.wallet(wallet) == null ? "" : book.wallet(wallet).name);
    }
    private void save() {
        if (reading || book == null || model.busy()) return;
        long amount;
        try { amount = FinanceBook.parseAmount(((EditText) findViewById(R.id.etAmount)).getText().toString()); }
        catch (FinanceBook.Failure error) { visible(R.id.tvAmountError, true); text(R.id.tvAmountError, getString(R.string.finance_amount_error)); return; }
        visible(R.id.tvAmountError, false);
        FinanceBook.Transaction value = new FinanceBook.Transaction(id, FinanceBook.Type.EXPENSE, amount, category, wallet, null, date, "", payee);
        model.change(FinanceAction.transaction(value, false), true);
    }
    @Override protected void loading(boolean loading) { findViewById(R.id.btnConfirmReceipt).setEnabled(!loading && !reading); }
    @Override protected void completed() {
        if (images != null && index + 1 < images.size()) startActivity(new Intent(this, ReceiptReviewActivity.class)
                .putStringArrayListExtra("images", images).putExtra("index", index + 1));
        finish();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putString("id", id); out.putString("category", category); out.putString("wallet", wallet);
        out.putString("payee", payee); out.putLong("date", date); out.putBoolean("applied", applied);
    }
}
