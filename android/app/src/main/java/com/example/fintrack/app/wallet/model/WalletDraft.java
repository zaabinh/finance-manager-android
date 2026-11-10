package com.example.fintrack.app.wallet.model;

import com.example.fintrack.app.finance.domain.FinanceBook;

/** Parsed and validated wallet form input shared by the wallet screens and Sheet/Wallet. */
public final class WalletDraft {
    public enum Error { NONE, NAME, OPENING, ORDER }
    public final Error error;
    public final String name;
    public final long opening;
    public final int sortOrder;

    private WalletDraft(Error error, String name, long opening, int sortOrder) {
        this.error = error; this.name = name; this.opening = opening; this.sortOrder = sortOrder;
    }

    /** openingText may be grouped ("1.000") or blank (= 0); orderText blank = 0. */
    public static WalletDraft parse(String nameText, String openingText, String orderText) {
        String name = nameText == null ? "" : nameText.trim();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > 100) return fail(Error.NAME);
        String digits = openingText == null ? "" : openingText.trim().replace(".", "");
        long opening = 0;
        if (!digits.isEmpty()) {
            if (!digits.matches("[0-9]{1,12}")) return fail(Error.OPENING);
            opening = Long.parseLong(digits);
            if (opening > FinanceBook.MAX_AMOUNT) return fail(Error.OPENING);
        }
        int order = 0;
        String o = orderText == null ? "" : orderText.trim();
        if (!o.isEmpty()) {
            try { order = Integer.parseInt(o); } catch (NumberFormatException e) { return fail(Error.ORDER); }
            if (order < 0) return fail(Error.ORDER);
        }
        return new WalletDraft(Error.NONE, name, opening, order);
    }

    private static WalletDraft fail(Error error) { return new WalletDraft(error, null, 0, 0); }
}
