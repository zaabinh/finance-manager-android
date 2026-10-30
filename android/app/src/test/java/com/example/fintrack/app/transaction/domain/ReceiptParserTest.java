package com.example.fintrack.app.transaction.domain;
import static org.junit.Assert.*;
import org.junit.Test;

public class ReceiptParserTest {
    @Test public void totalIsSuggestedWithoutUsingPhoneOrChange() {
        ReceiptParser.Draft draft = ReceiptParser.parse("Quán Đồng\nĐT: 0909123456\nTỔNG CỘNG 95.000 đ\nKhách đưa 100.000\nTiền thừa 5.000");
        assertEquals(95_000, draft.amount); assertEquals("Quán Đồng", draft.payee);
    }
    @Test public void supportsCommaSeparatedVndAndDoesNotUseSubtotal() {
        assertEquals(120_000, ReceiptParser.parse("Shop\nSubtotal 100,000\nTOTAL 120,000 VND").amount);
    }
    @Test public void ambiguousTotalsStayEmptyForManualReview() {
        assertEquals(0, ReceiptParser.parse("Shop\nTổng tiền 50.000\nThanh toán 60.000").amount);
        assertEquals(0, ReceiptParser.parse("Shop\nLatte 95.000\n0909123456").amount);
    }
    @Test public void repeatedSameTotalIsNotAnAmbiguity() {
        assertEquals(95_000, ReceiptParser.parse("Shop\nTổng tiền 95.000\nThanh toán 95.000").amount);
    }
    @Test public void multipleNumbersOnTotalLineRequireManualReview() {
        assertEquals(0, ReceiptParser.parse("Shop\nTOTAL 95.000 VAT 10%").amount);
    }
    @Test public void emptyAndMalformedInputNeverProduceFinancialData() {
        assertEquals(0, ReceiptParser.parse(null).amount); assertEquals("", ReceiptParser.parse("").payee);
        assertEquals(0, ReceiptParser.parse("TOTAL 9.50").amount);
    }
}
