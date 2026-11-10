package com.example.fintrack.app.wallet.ui;

import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.data.FinanceRepository;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.core.ui.MoneyInput;
import com.example.fintrack.app.wallet.model.WalletDraft;

/** Forms use the authenticated finance repository and command lifecycle. */
public abstract class WalletFormActivity extends FinanceActivity {
    private EditText name, opening, order;
    private CheckBox assets;
    private String id;
    private boolean initialized;
    private FinanceBook.WalletType type = FinanceBook.WalletType.CASH;
    protected abstract boolean editing();
    @Override protected int layout() { return editing() ? R.layout.activity_wallet_edit : R.layout.activity_wallet_add; }
    @Override protected FinanceRepository.Scope readScope() { return FinanceRepository.Scope.WALLETS; }
    @Override protected void bind(Bundle saved) {
        id = getIntent().getStringExtra("WALLET_ID");
        name = findViewById(R.id.etWalletName); opening = findViewById(R.id.etBalance);
        order = findViewById(R.id.etWalletSortOrder); assets = findViewById(R.id.cbWalletIncludeAssets);
        MoneyInput.attach(opening, findViewById(R.id.chipGroupAmountSuggestions));
        if (saved != null) {
            initialized = saved.getBoolean("walletInitialized");
            type = FinanceBook.WalletType.valueOf(saved.getString("walletType", "CASH"));
        }
        toolbar(R.id.toolbarForm);
        findViewById(R.id.fieldWalletType).setOnClickListener(v -> WalletTypes.choose(this, selected -> { type = selected; showType(); }));
        findViewById(editing() ? R.id.btnSaveChanges : R.id.btnCreateWallet).setOnClickListener(v -> save());
        if (!editing()) findViewById(R.id.btnCancel).setOnClickListener(v -> finish());
        showType();
    }
    @Override protected void render() {
        if (editing() && !initialized) {
            FinanceBook.Wallet wallet = book.wallet(id);
            if (wallet == null || wallet.archivedAt != null) {
                Toast.makeText(this, R.string.wallet_missing_or_archived, Toast.LENGTH_LONG).show(); finish(); return;
            }
            name.setText(wallet.name); opening.setText(Long.toString(wallet.openingBalance));
            order.setText(Integer.toString(wallet.sortOrder)); assets.setChecked(wallet.includeInAssets);
            type = wallet.walletType; initialized = true; showType();
        }
    }
    private void showType() {
        text(R.id.tvWalletTypeValue, getString(WalletTypes.label(type)));
        ((android.widget.ImageView) findViewById(R.id.ivWalletTypeIcon)).setImageResource(WalletTypes.icon(type));
    }
    private void save() {
        if (book == null) return;
        WalletDraft draft = WalletDraft.parse(name.getText().toString(), opening.getText().toString(), order.getText().toString());
        if (draft.error == WalletDraft.Error.NAME) { name.setError(getString(R.string.wallet_invalid_name)); return; }
        if (draft.error == WalletDraft.Error.OPENING) { opening.setError(getString(R.string.wallet_invalid_opening)); return; }
        if (draft.error == WalletDraft.Error.ORDER) { order.setError(getString(R.string.wallet_invalid_order)); return; }
        model.change(editing() ? FinanceAction.walletUpdate(id, draft.name, draft.opening, type, assets.isChecked(), draft.sortOrder)
                : FinanceAction.wallet(draft.name, draft.opening, type, assets.isChecked(), draft.sortOrder), true);
    }
    @Override protected void loading(boolean value) {
        FinanceUi.progress((android.widget.Button) findViewById(editing() ? R.id.btnSaveChanges : R.id.btnCreateWallet), value);
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putBoolean("walletInitialized", initialized); out.putString("walletType", type.name());
    }
}
