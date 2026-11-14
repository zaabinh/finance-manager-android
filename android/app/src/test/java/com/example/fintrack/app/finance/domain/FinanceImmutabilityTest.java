package com.example.fintrack.app.finance.domain;

import static org.junit.Assert.*;
import org.junit.Test;

public class FinanceImmutabilityTest {
    private FinanceBook book() {
        FinanceSnapshot.Builder state=FinanceBook.empty();
        state.wallets.add(new Wallet("cash","Cash",1000));
        state.wallets.add(new Wallet("bank","Bank",1000));
        return new FinanceBook(state);
    }
    @Test public void builderAndSnapshotCannotModifyTheBook() {
        FinanceSnapshot.Builder state=FinanceBook.empty();
        state.wallets.add(new Wallet("cash","Cash",1000));
        FinanceBook book=new FinanceBook(state);
        state.wallets.clear(); state.categories.clear();
        assertEquals(1000,book.balance("cash"));
        assertNotNull(book.category("food"));
        assertThrows(UnsupportedOperationException.class,()->book.snapshot().wallets.clear());
        assertThrows(UnsupportedOperationException.class,()->book.wallets().clear());
        assertThrows(UnsupportedOperationException.class,()->book.categories().clear());
    }
    @Test public void saveNormalizesReplacementWithoutModifyingInput() {
        FinanceBook book=book();
        Transaction input=new Transaction("t",TransactionType.TRANSFER,100,"food","cash","bank",0," note ",null)
                .withClientRequestId("stable");
        book.save(input,false);
        assertEquals("food",input.categoryId); assertEquals(" note ",input.note); assertNull(input.payee);
        assertNull(book.transaction("t").categoryId); assertEquals("note",book.transaction("t").note);
        assertEquals("",book.transaction("t").payee); assertEquals("stable",book.transaction("t").clientRequestId);
    }
    @Test public void oldSnapshotSurvivesVoidAndArchive() {
        FinanceBook book=book();
        book.save(new Transaction("t",TransactionType.EXPENSE,100,"food","cash",null,0,"",""),false);
        FinanceSnapshot before=book.snapshot();
        Wallet wallet=book.wallet("cash");
        book.deleteTransaction("t");book.walletStatus("cash",false);book.categoryStatus("food",false);
        assertEquals("POSTED",before.transactions.get(0).status);
        assertNull(wallet.archivedAt); assertNull(before.categories.get(0).archivedAt);
        assertEquals("VOIDED",book.transaction("t").status);assertNotNull(book.wallet("cash").archivedAt);
        assertNotNull(book.category("food").archivedAt);
    }
    @Test public void nullAndZeroServerAggregatesRemainDistinct() {
        FinanceSnapshot.Builder state=FinanceBook.empty();state.serverSnapshot=true;
        state.wallets.add(new Wallet("cash","Cash",1000,WalletType.CASH,null,true,0,0L));
        state.monthlyBudgets.add(new MonthlyBudget("m","2026-10",null,0L,0L,80));
        FinanceBook book=new FinanceBook(state);
        assertEquals(0,book.balance("cash"));assertEquals(0,book.assets(false));
        assertEquals(0,book.monthlyTotal(book.monthlyBudget("m")));
        assertEquals(0,book.monthlySpent(book.monthlyBudget("m")));
        assertEquals(0,book.monthlyUsage(book.monthlyBudget("m")).progress);
    }
    @Test public void pageCopiesItsInputAndRejectsMutation() {
        java.util.List<Transaction> source=new java.util.ArrayList<>();
        FinanceBook.Page page=new FinanceBook.Page(source,0,0);
        source.add(new Transaction("x",TransactionType.INCOME,1,"salary","cash",null,0,"",""));
        assertTrue(page.items.isEmpty());assertThrows(UnsupportedOperationException.class,()->page.items.add(source.get(0)));
    }
}
