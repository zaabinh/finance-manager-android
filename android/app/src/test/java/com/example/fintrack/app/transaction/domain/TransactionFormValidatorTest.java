package com.example.fintrack.app.transaction.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.TransactionType;
import com.example.fintrack.app.transaction.domain.TransactionFormValidator.Error;
import com.example.fintrack.app.transaction.domain.TransactionFormValidator.Result;
import org.junit.Before;
import org.junit.Test;

public class TransactionFormValidatorTest {
    private FinanceBook book;
    private String cash, bank, oldWallet, food, salary, oldCategory;

    @Before public void setup() {
        book = new FinanceBook(FinanceBook.empty());
        cash = book.addWallet("Tiền mặt", 0).id;
        bank = book.addWallet("Ngân hàng", 0).id;
        oldWallet = book.addWallet("Ví cũ", 0).id;
        book.walletStatus(oldWallet, false);
        food = book.addCategory("Test chi tiêu", TransactionType.EXPENSE).id;
        salary = book.addCategory("Test thu nhập", TransactionType.INCOME).id;
        oldCategory = book.addCategory("Test danh mục cũ", TransactionType.EXPENSE).id;
        book.categoryStatus(oldCategory, false);
    }

    private Result run(TransactionType type, String category, String wallet, String destination, String amount) {
        return TransactionFormValidator.validate(book, "t1", type, category, wallet, destination, 1000L, amount, "note", "payee");
    }

    @Test public void validExpenseKeepsCategoryAndDropsDestination() {
        Result result = run(TransactionType.EXPENSE, food, cash, bank, "150.000");
        assertTrue(result.valid());
        assertEquals(150_000L, result.transaction.amount);
        assertEquals(food, result.transaction.categoryId);
        assertNull(result.transaction.destinationWalletId);
        assertEquals("note", result.transaction.note);
        assertEquals("payee", result.transaction.payee);
        assertNull(result.transaction.clientRequestId);
    }

    @Test public void validIncome() {
        Result result = run(TransactionType.INCOME, salary, cash, null, "5000000");
        assertTrue(result.valid());
        assertEquals(TransactionType.INCOME, result.transaction.type);
        assertEquals(5_000_000L, result.transaction.amount);
    }

    @Test public void validTransferDropsCategoryAndKeepsDestination() {
        Result result = run(TransactionType.TRANSFER, food, cash, bank, "1.000");
        assertTrue(result.valid());
        assertNull(result.transaction.categoryId);
        assertEquals(bank, result.transaction.destinationWalletId);
    }

    @Test public void transferNeedsNoCategory() {
        assertTrue(run(TransactionType.TRANSFER, null, cash, bank, "1000").valid());
    }

    @Test public void amountMustBePositiveWellFormedAndWithinLimit() {
        for (String amount : new String[] {"0", "", "abc", "-5", "1.5", "1000.000", "1.000.000.000.000", "0.000"})
            assertEquals(amount, Error.INVALID_AMOUNT, run(TransactionType.EXPENSE, food, cash, null, amount).error);
        assertEquals(Error.INVALID_AMOUNT, run(TransactionType.EXPENSE, food, cash, null, null).error);
        assertTrue(run(TransactionType.EXPENSE, food, cash, null, "999.999.999.999").valid());
    }

    @Test public void missingCategoryOrWallet() {
        assertEquals(Error.MISSING_CATEGORY, run(TransactionType.EXPENSE, null, cash, null, "1000").error);
        assertEquals(Error.MISSING_CATEGORY, run(TransactionType.INCOME, "", cash, null, "1000").error);
        assertEquals(Error.MISSING_WALLET, run(TransactionType.EXPENSE, food, null, null, "1000").error);
        assertEquals(Error.MISSING_WALLET, run(TransactionType.EXPENSE, food, "", null, "1000").error);
        assertEquals(Error.MISSING_TRANSFER_WALLETS, run(TransactionType.TRANSFER, null, cash, null, "1000").error);
        assertEquals(Error.MISSING_TRANSFER_WALLETS, run(TransactionType.TRANSFER, null, null, bank, "1000").error);
    }

    @Test public void transferBetweenSameWalletIsRejected() {
        assertEquals(Error.SAME_WALLET, run(TransactionType.TRANSFER, null, cash, cash, "1000").error);
    }

    @Test public void archivedOrUnknownReferencesAreRejected() {
        assertEquals(Error.WALLET_ARCHIVED, run(TransactionType.EXPENSE, food, oldWallet, null, "1000").error);
        assertEquals(Error.WALLET_ARCHIVED, run(TransactionType.EXPENSE, food, "missing", null, "1000").error);
        assertEquals(Error.WALLET_ARCHIVED, run(TransactionType.TRANSFER, null, cash, oldWallet, "1000").error);
        assertEquals(Error.CATEGORY_ARCHIVED, run(TransactionType.EXPENSE, oldCategory, cash, null, "1000").error);
        assertEquals(Error.CATEGORY_ARCHIVED, run(TransactionType.EXPENSE, "missing", cash, null, "1000").error);
        // A category of the other type is not usable.
        assertEquals(Error.CATEGORY_ARCHIVED, run(TransactionType.EXPENSE, salary, cash, null, "1000").error);
    }

    @Test public void checksRunInTheDocumentedOrder() {
        assertEquals(Error.MISSING_CATEGORY, run(TransactionType.EXPENSE, null, null, null, "bad").error);
        assertEquals(Error.MISSING_WALLET, run(TransactionType.EXPENSE, food, null, null, "bad").error);
        assertEquals(Error.WALLET_ARCHIVED, run(TransactionType.EXPENSE, food, oldWallet, null, "bad").error);
        assertEquals(Error.CATEGORY_ARCHIVED, run(TransactionType.EXPENSE, oldCategory, cash, null, "bad").error);
        assertEquals(Error.INVALID_AMOUNT, run(TransactionType.EXPENSE, food, cash, null, "bad").error);
    }
}
