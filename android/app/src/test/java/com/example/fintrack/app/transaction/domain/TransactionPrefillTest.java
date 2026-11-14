package com.example.fintrack.app.transaction.domain;

import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.TransactionType;
import com.example.fintrack.app.finance.domain.WalletType;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class TransactionPrefillTest {
    private FinanceBook book;
    private String cash, savings, archived;

    @Before public void setup() {
        book = new FinanceBook(FinanceBook.empty());
        cash = book.addWallet("Tiền mặt", 0).id;
        savings = book.addWallet("Tiết kiệm", 5_000_000, WalletType.SAVINGS).id;
        archived = book.addWallet("Ví cũ", 0).id;
        book.walletStatus(archived, false);
    }

    @Test public void budgetShortfallPrefillFillsTransferForm() {
        TransactionPrefill prefill = TransactionPrefill.parse("TRANSFER", savings, cash, 250_000L).resolve(book);
        assertEquals(TransactionType.TRANSFER, prefill.type);
        assertEquals(savings, prefill.walletId);
        assertEquals(cash, prefill.destinationWalletId);
        assertEquals(Long.valueOf(250_000L), prefill.amountMinor);
    }

    @Test public void optionalFieldsMayBeAbsent() {
        TransactionPrefill prefill = TransactionPrefill.parse("TRANSFER", null, null, null).resolve(book);
        assertEquals(TransactionType.TRANSFER, prefill.type);
        assertNull(prefill.walletId); assertNull(prefill.destinationWalletId); assertNull(prefill.amountMinor);
        assertNull(TransactionPrefill.parse(null, null, " ", null));
    }

    @Test public void unknownArchivedOrSameWalletIdsAreIgnored() {
        TransactionPrefill prefill = TransactionPrefill.parse("TRANSFER", "missing", archived, 1_000L).resolve(book);
        assertNull(prefill.walletId); assertNull(prefill.destinationWalletId);
        assertEquals(Long.valueOf(1_000L), prefill.amountMinor);
        TransactionPrefill same = TransactionPrefill.parse("TRANSFER", savings, savings, null).resolve(book);
        assertEquals(savings, same.walletId); assertNull(same.destinationWalletId);
    }

    @Test public void invalidTypeAndAmountAreIgnored() {
        TransactionPrefill prefill = TransactionPrefill.parse("transfer", cash, savings, 0L).resolve(book);
        assertEquals(TransactionType.EXPENSE, prefill.type); // unknown type keeps the form default
        assertEquals(cash, prefill.walletId);
        assertNull(prefill.destinationWalletId); // a destination only applies to a transfer
        assertNull(prefill.amountMinor);
        assertNull(TransactionPrefill.parse("TRANSFER", null, null, -5L).amountMinor);
        assertNull(TransactionPrefill.parse("TRANSFER", null, null, FinanceBook.MAX_AMOUNT + 1).amountMinor);
        assertEquals(Long.valueOf(FinanceBook.MAX_AMOUNT), TransactionPrefill.parse("TRANSFER", null, null, FinanceBook.MAX_AMOUNT).amountMinor);
    }

    @Test public void categoryPrefillResolvedWhenActiveAndMatchingType() {
        TransactionPrefill prefill = TransactionPrefill.parse("EXPENSE", cash, null, 50_000L, "food").resolve(book);
        assertEquals(TransactionType.EXPENSE, prefill.type);
        assertEquals("food", prefill.categoryId);

        // Category not applicable on transfer
        TransactionPrefill transferPrefill = TransactionPrefill.parse("TRANSFER", savings, cash, 50_000L, "food").resolve(book);
        assertNull(transferPrefill.categoryId);

        // Unknown category is ignored
        TransactionPrefill unknownPrefill = TransactionPrefill.parse("EXPENSE", cash, null, 50_000L, "unknown-cat").resolve(book);
        assertNull(unknownPrefill.categoryId);

        // Mismatched category type (salary is INCOME, but type is EXPENSE) is ignored
        TransactionPrefill mismatchPrefill = TransactionPrefill.parse("EXPENSE", cash, null, 50_000L, "salary").resolve(book);
        assertNull(mismatchPrefill.categoryId);
    }
}
