package com.example.fintrack.app.finance.domain;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/** Mock parity for GET /api/v1/transactions and the Android page merge. */
public class TransactionQueryTest {
    private FinanceBook book;
    private String cash, bank;
    // 2026-10-31T17:00:00Z is 2026-11-01 00:00 in Asia/Ho_Chi_Minh.
    private static final long NOV_FIRST_VN = 1793466000000L;

    @Before public void setup() {
        book = new FinanceBook(FinanceBook.empty());
        cash = book.addWallet("Tiền mặt", 10_000_000).id;
        bank = book.addWallet("Ngân hàng", 0, FinanceBook.WalletType.BANK).id;
    }
    private FinanceBook.Transaction add(String id, FinanceBook.Type type, long amount, String category, String wallet, long at, String note, String payee) {
        FinanceBook.Transaction value = new FinanceBook.Transaction(id, type, amount, category, wallet, null, at, note, payee);
        book.save(value, false); return value;
    }
    private static List<String> ids(List<FinanceBook.Transaction> rows) {
        List<String> result = new ArrayList<>(); for (FinanceBook.Transaction row : rows) result.add(row.id); return result;
    }
    private FinanceBook.Query november() { return FinanceBook.Query.month("2026-11", null, null, null, null); }

    @Test public void dateRangeIsInclusiveInUserTimezoneAndExcludesVoided() {
        add("oct", FinanceBook.Type.EXPENSE, 1, "food", cash, NOV_FIRST_VN - 1, "", "");
        add("nov", FinanceBook.Type.EXPENSE, 2, "food", cash, NOV_FIRST_VN, "", "");
        add("void", FinanceBook.Type.EXPENSE, 3, "food", cash, NOV_FIRST_VN + 1, "", "");
        book.deleteTransaction("void");
        assertEquals(Arrays.asList("nov"), ids(book.query(november(), 0, 50).items));
        assertEquals("2026-11-30", november().dateTo);
    }
    @Test public void filtersAndLiteralCaseInsensitiveSearchOnNoteAndPayee() {
        add("a", FinanceBook.Type.EXPENSE, 10, "food", cash, NOV_FIRST_VN, "Giảm 50% cà phê", "");
        add("b", FinanceBook.Type.EXPENSE, 20, "transport", cash, NOV_FIRST_VN, "50 lần", "QUÁN ĐỒNG");
        add("c", FinanceBook.Type.INCOME, 30, "salary", bank, NOV_FIRST_VN, "", "Công ty");
        assertEquals(Arrays.asList("a"), ids(book.query(FinanceBook.Query.month("2026-11", null, null, null, "50%"), 0, 50).items));
        assertEquals(Arrays.asList("b"), ids(book.query(FinanceBook.Query.month("2026-11", null, null, null, "quán đồng"), 0, 50).items));
        assertEquals(Arrays.asList("c"), ids(book.query(FinanceBook.Query.month("2026-11", FinanceBook.Type.INCOME, null, null, null), 0, 50).items));
        assertEquals(Arrays.asList("c"), ids(book.query(FinanceBook.Query.month("2026-11", null, bank, null, null), 0, 50).items));
        assertEquals(Arrays.asList("b"), ids(book.query(FinanceBook.Query.month("2026-11", null, null, "transport", null), 0, 50).items));
        // Category/wallet names are not part of the server search (contract: note/payee only).
        assertTrue(book.query(FinanceBook.Query.month("2026-11", null, null, null, "Ăn uống"), 0, 50).items.isEmpty());
    }
    @Test public void sortHasIdTieBreakerAndPagesReportTotalPages() {
        for (int i = 0; i < 5; i++) add("t" + i, FinanceBook.Type.EXPENSE, 100 + i, "food", cash, NOV_FIRST_VN + (i % 2), "", "");
        FinanceBook.Page first = book.query(november(), 0, 2), last = book.query(november(), 2, 2), beyond = book.query(november(), 9, 2);
        assertEquals(Arrays.asList("t3", "t1"), ids(first.items)); assertEquals(3, first.totalPages);
        assertEquals(Arrays.asList("t0"), ids(last.items));
        assertTrue(beyond.items.isEmpty()); assertEquals(3, beyond.totalPages);
        FinanceBook.Query byAmount = new FinanceBook.Query("2026-11-01", "2026-11-30", null, null, null, null, "amountMinor,asc");
        assertEquals(Arrays.asList("t0", "t1", "t2", "t3", "t4"), ids(book.query(byAmount, 0, 50).items));
    }
    @Test public void mergeDeduplicatesRowsThatShiftedAcrossPages() {
        FinanceBook.Transaction a = add("a", FinanceBook.Type.EXPENSE, 1, "food", cash, NOV_FIRST_VN, "", "");
        FinanceBook.Transaction b = add("b", FinanceBook.Type.EXPENSE, 2, "food", cash, NOV_FIRST_VN, "", "");
        FinanceBook.Transaction c = add("c", FinanceBook.Type.EXPENSE, 3, "food", cash, NOV_FIRST_VN, "", "");
        List<FinanceBook.Transaction> merged = FinanceBook.merge(Arrays.asList(a, b), Arrays.asList(b, c));
        assertEquals(Arrays.asList("a", "b", "c"), ids(merged));
    }
    @Test public void queryEqualityKeepsStateAndSearchIsTrimmedAndCapped() {
        assertEquals(FinanceBook.Query.month("2026-11", null, "w", null, "  cà phê "), FinanceBook.Query.month("2026-11", null, "w", null, "cà phê"));
        assertNotEquals(november(), FinanceBook.Query.month("2026-11", FinanceBook.Type.EXPENSE, null, null, null));
        assertNull(FinanceBook.Query.month("2026-11", null, null, null, "   ").q);
        char[] longText = new char[250]; Arrays.fill(longText, 'a');
        assertEquals(200, FinanceBook.Query.month("2026-11", null, null, null, new String(longText)).q.length());
    }
    @Test public void mockSeedsMatchContractCategories() {
        List<String> expense = new ArrayList<>(), income = new ArrayList<>();
        for (FinanceBook.Category category : FinanceBook.empty().categories)
            (category.type == FinanceBook.Type.EXPENSE ? expense : income).add(category.name);
        assertEquals(Arrays.asList("Ăn uống", "Đi lại", "Mua sắm", "Hóa đơn", "Khác"), expense);
        assertEquals(Arrays.asList("Lương", "Thưởng", "Thu nhập khác"), income);
    }
}
