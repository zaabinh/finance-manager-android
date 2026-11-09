package com.example.fintrack.app.wallet.ui;

import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.data.FinanceRepository;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.core.ui.MoneyInput;
import com.example.fintrack.app.setup.model.PendingWallet;

/** Forms use the authenticated finance repository and command lifecycle. */
public abstract class WalletFormActivity extends FinanceActivity {
    /** When true (create mode only) the screen returns a {@link PendingWallet} result instead of saving. */
    public static final String EXTRA_RETURN_RESULT = "RETURN_RESULT";
    public static final String EXTRA_RESULT_WALLET = "RESULT_WALLET";
    private boolean returnResult;
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
        returnResult = !editing() && getIntent().getBooleanExtra(EXTRA_RETURN_RESULT, false);
        MoneyInput.attach(opening, findViewById(R.id.chipGroupAmountSuggestions));
        if (saved != null) {
            initialized = saved.getBoolean("walletInitialized");
            type = FinanceBook.WalletType.valueOf(saved.getString("walletType", "CASH"));
        }
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        int[] pills = {R.id.pillCash, R.id.pillBank, R.id.pillSavings};
        FinanceBook.WalletType[] types = FinanceBook.WalletType.values();
        for (int i = 0; i < pills.length; i++) {
            final FinanceBook.WalletType selected = types[i];
            findViewById(pills[i]).setOnClickListener(v -> { type = selected; showType(); });
        }
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
        int[] pills = {R.id.pillCash, R.id.pillBank, R.id.pillSavings};
        FinanceBook.WalletType[] types = FinanceBook.WalletType.values();
        for (int i = 0; i < pills.length; i++) {
            TextView pill = findViewById(pills[i]); boolean selected = types[i] == type;
            pill.setBackgroundResource(selected ? R.drawable.bg_pill_selected : R.drawable.bg_pill_unselected);
            pill.setTextColor(getColor(selected ? R.color.white : R.color.text_secondary));
        }
    }
    private void save() {
        if (book == null && !returnResult) return;
        String value = name.getText().toString().trim();
        if (value.isEmpty() || value.codePointCount(0, value.length()) > 100) {
            name.setError(getString(R.string.wallet_invalid_name)); return;
        }
        long amount; int sort;
        try {
            String digits = opening.getText().toString().trim();
            if (digits.isEmpty()) digits = "0";
            digits = digits.replace(".", "");
            if (!digits.matches("[0-9]+")) throw new NumberFormatException();
            amount = Long.parseLong(digits);
            if (amount > FinanceBook.MAX_AMOUNT) throw new NumberFormatException();
        } catch (NumberFormatException e) { opening.setError(getString(R.string.wallet_invalid_opening)); return; }
        try {
            String digits = order.getText().toString().trim(); sort = digits.isEmpty() ? 0 : Integer.parseInt(digits);
            if (sort < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) { order.setError(getString(R.string.wallet_invalid_order)); return; }
        if (returnResult) {
            setResult(RESULT_OK, new android.content.Intent().putExtra(EXTRA_RESULT_WALLET,
                    new PendingWallet(value, type.name(), amount, assets.isChecked(), sort)));
            finish(); return;
        }
        model.change(editing() ? FinanceAction.walletUpdate(id, value, amount, type, assets.isChecked(), sort)
                : FinanceAction.wallet(value, amount, type, assets.isChecked(), sort), true);
    }
    @Override protected void loading(boolean value) {
        FinanceUi.progress((android.widget.Button) findViewById(editing() ? R.id.btnSaveChanges : R.id.btnCreateWallet), value);
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putBoolean("walletInitialized", initialized); out.putString("walletType", type.name());
    }
}
