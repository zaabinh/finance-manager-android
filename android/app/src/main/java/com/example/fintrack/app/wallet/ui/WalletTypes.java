package com.example.fintrack.app.wallet.ui;

import android.content.Context;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import com.example.fintrack.R;
import com.example.fintrack.app.finance.domain.FinanceBook.WalletType;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.function.Consumer;

/** Labels, icons and the chooser for the wallet-type Field/Selector (sheet and screens). */
public final class WalletTypes {
    private WalletTypes() { }

    @StringRes public static int label(WalletType type) {
        return type == WalletType.BANK ? R.string.wallet_type_bank : type == WalletType.SAVINGS ? R.string.wallet_type_savings
                : R.string.wallet_type_cash;
    }
    @DrawableRes public static int icon(WalletType type) {
        return type == WalletType.BANK ? R.drawable.ic_landmark : type == WalletType.SAVINGS ? R.drawable.ic_target
                : R.drawable.ic_banknote;
    }
    public static void choose(Context context, Consumer<WalletType> selected) {
        WalletType[] types = WalletType.values();
        CharSequence[] names = new CharSequence[types.length];
        for (int i = 0; i < types.length; i++) names[i] = context.getString(label(types[i]));
        new MaterialAlertDialogBuilder(context).setTitle(R.string.wallet_type_label)
                .setItems(names, (dialog, which) -> selected.accept(types[which])).show();
    }
}
