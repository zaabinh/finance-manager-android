package com.example.fintrack.app.setup.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Wallets gathered in the first-setup wallet step. The default cash wallet, once confirmed, is always first. */
public final class PendingWalletList implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final String CASH_NAME = "Tiền mặt";
    private final ArrayList<PendingWallet> items = new ArrayList<>();

    /** Adds or replaces the default cash wallet with the given opening balance. */
    public void setCash(long balance) {
        PendingWallet cash = new PendingWallet(CASH_NAME, "CASH", balance, true, 0);
        if (hasCash()) items.set(0, cash); else items.add(0, cash);
    }

    public boolean hasCash() { return !items.isEmpty() && isCash(items.get(0)); }

    public long cashBalance() { return hasCash() ? items.get(0).openingBalance : 0; }

    public void add(PendingWallet wallet) { items.add(wallet); }

    /** Wallets added besides the default cash wallet. */
    public List<PendingWallet> others() {
        return Collections.unmodifiableList(hasCash() ? items.subList(1, items.size()) : items);
    }

    public List<PendingWallet> all() { return Collections.unmodifiableList(items); }

    public int size() { return items.size(); }

    private static boolean isCash(PendingWallet wallet) { return CASH_NAME.equals(wallet.name) && "CASH".equals(wallet.walletType); }
}
