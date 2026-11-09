package com.example.fintrack.app.wallet.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.fintrack.R;
import com.example.fintrack.app.core.ui.MoneyInput;
import com.example.fintrack.app.core.ui.SheetDialog;
import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.ui.FinanceUi;
import com.example.fintrack.app.setup.model.PendingWallet;
import com.example.fintrack.app.wallet.model.WalletDraft;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

/** Sheet/Wallet (Figma 718:11413, Add): quick wallet creation. The caller decides what saving means. */
public final class WalletSheet {
    public interface Listener { void onSave(WalletSheet sheet, PendingWallet wallet); }

    private final BottomSheetDialog dialog;
    private final MaterialButton confirm;
    private FinanceBook.WalletType type = FinanceBook.WalletType.CASH;

    private WalletSheet(AppCompatActivity host, Listener listener) {
        View content = LayoutInflater.from(host).inflate(R.layout.sheet_wallet, null);
        EditText name = content.findViewById(R.id.etWalletName), balance = content.findViewById(R.id.etBalance);
        MoneyInput.attach(balance, content.findViewById(R.id.chipGroupAmountSuggestions));
        ((TextView) content.findViewById(R.id.tvSheetTitle)).setText(R.string.wallet_add_title);
        confirm = content.findViewById(R.id.btnSheetConfirm);
        confirm.setText(R.string.wallet_add_title);
        showType(content);
        content.findViewById(R.id.fieldWalletType).setOnClickListener(v ->
                WalletTypes.choose(host, selected -> { type = selected; showType(content); }));
        dialog = SheetDialog.show(host, content);
        content.findViewById(R.id.btnSheetClose).setOnClickListener(v -> dialog.dismiss());
        content.findViewById(R.id.btnSheetCancel).setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            WalletDraft draft = WalletDraft.parse(name.getText().toString(), balance.getText().toString(), null);
            if (draft.error == WalletDraft.Error.NAME) { name.setError(host.getString(R.string.wallet_invalid_name)); return; }
            if (draft.error == WalletDraft.Error.OPENING) { balance.setError(host.getString(R.string.wallet_invalid_opening)); return; }
            listener.onSave(this, new PendingWallet(draft.name, type.name(), draft.opening, true, 0));
        });
    }

    public static WalletSheet show(AppCompatActivity host, Listener listener) { return new WalletSheet(host, listener); }

    public void loading(boolean busy) { FinanceUi.progress(confirm, busy); }
    public void dismiss() { dialog.dismiss(); }

    private void showType(View content) {
        ((TextView) content.findViewById(R.id.tvWalletTypeValue)).setText(WalletTypes.label(type));
        ((ImageView) content.findViewById(R.id.ivWalletTypeIcon)).setImageResource(WalletTypes.icon(type));
    }
}
