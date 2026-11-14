package com.example.fintrack.app.wallet.ui;

import com.example.fintrack.app.finance.domain.Wallet;
import com.example.fintrack.app.finance.domain.WalletType;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.Toolbar;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.data.FinanceAction;
import com.example.fintrack.app.finance.data.FinanceRepository;
import com.example.fintrack.app.finance.ui.FinanceActivity;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.core.ui.MoneyInput;
import com.example.fintrack.app.wallet.model.WalletDraft;
import com.example.fintrack.databinding.ActivityWalletAddBinding;
import com.example.fintrack.databinding.ActivityWalletEditBinding;
import com.google.android.material.chip.ChipGroup;

/** Forms use the authenticated finance repository and command lifecycle. */
public abstract class WalletFormActivity extends FinanceActivity {
    private static final String STATE_WALLET_INITIALIZED = "walletInitialized", STATE_WALLET_TYPE = "walletType";
    // The add and edit layouts are different binding classes; the shared views are held once here.
    private View root;
    private Toolbar toolbar;
    private EditText name, opening, order;
    private CheckBox assets;
    private Button save, cancel;
    private TextView typeValue;
    private ImageView typeIcon;
    private String id;
    private boolean initialized;
    private WalletType type = WalletType.CASH;
    protected abstract boolean editing();
    @Override protected View contentView() {
        if (editing()) {
            ActivityWalletEditBinding b = ActivityWalletEditBinding.inflate(getLayoutInflater());
            root = b.getRoot(); toolbar = b.toolbarForm; order = b.etWalletSortOrder; assets = b.cbWalletIncludeAssets; save = b.btnSaveChanges;
        } else {
            ActivityWalletAddBinding b = ActivityWalletAddBinding.inflate(getLayoutInflater());
            root = b.getRoot(); toolbar = b.toolbarForm; order = b.etWalletSortOrder; assets = b.cbWalletIncludeAssets;
            save = b.btnCreateWallet; cancel = b.btnCancel;
        }
        // form_wallet_fields is included without an android:id, so ViewBinding does not expose its views.
        name = root.findViewById(R.id.etWalletName); opening = root.findViewById(R.id.etBalance);
        typeValue = root.findViewById(R.id.tvWalletTypeValue); typeIcon = root.findViewById(R.id.ivWalletTypeIcon);
        return root;
    }
    @Override protected FinanceRepository.Scope readScope() { return FinanceRepository.Scope.WALLETS; }
    @Override protected void bind(Bundle saved) {
        id = getIntent().getStringExtra(WalletDetailActivity.EXTRA_WALLET_ID);
        MoneyInput.attach(opening, root.<ChipGroup>findViewById(R.id.chipGroupAmountSuggestions));
        if (saved != null) {
            initialized = saved.getBoolean(STATE_WALLET_INITIALIZED);
            type = WalletType.valueOf(saved.getString(STATE_WALLET_TYPE, "CASH"));
        }
        toolbar.setNavigationOnClickListener(view -> finish());
        root.findViewById(R.id.fieldWalletType).setOnClickListener(v -> WalletTypes.choose(this, selected -> { type = selected; showType(); }));
        save.setOnClickListener(v -> save());
        if (cancel != null) cancel.setOnClickListener(v -> finish());
        showType();
    }
    @Override protected void render() {
        if (editing() && !initialized) {
            Wallet wallet = book.wallet(id);
            if (wallet == null || wallet.archivedAt != null) {
                Toast.makeText(this, R.string.wallet_missing_or_archived, Toast.LENGTH_LONG).show(); finish(); return;
            }
            name.setText(wallet.name); opening.setText(Long.toString(wallet.openingBalance));
            order.setText(Integer.toString(wallet.sortOrder)); assets.setChecked(wallet.includeInAssets);
            type = wallet.walletType; initialized = true; showType();
        }
    }
    private void showType() {
        typeValue.setText(getString(WalletTypes.label(type)));
        typeIcon.setImageResource(WalletTypes.icon(type));
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
        if (save != null) FinanceUi.progress(save, value);
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putBoolean(STATE_WALLET_INITIALIZED, initialized); out.putString(STATE_WALLET_TYPE, type.name());
    }
}
