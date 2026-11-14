package com.example.fintrack.app.transaction.domain;

import static org.junit.Assert.*;

import com.example.fintrack.app.finance.domain.FinanceBook;

import org.junit.Test;

import java.util.Calendar;

public class ReceiptParserTest {

    // === Các bài test nguyên bản (Backward Compatibility Tests) ===

    @Test
    public void totalIsSuggestedWithoutUsingPhoneOrChange() {
        TransactionParser.Draft draft = TransactionParser.parse("Quán Đồng\nĐT: 0909123456\nTỔNG CỘNG 95.000 đ\nKhách đưa 100.000\nTiền thừa 5.000");
        assertEquals(95_000, draft.amount);
        assertEquals("Quán Đồng", draft.payee);
    }

    @Test
    public void supportsCommaSeparatedVndAndDoesNotUseSubtotal() {
        assertEquals(120_000, TransactionParser.parse("Shop\nSubtotal 100,000\nTOTAL 120,000 VND").amount);
    }

    @Test
    public void ambiguousTotalsStayEmptyForManualReview() {
        assertEquals(0, TransactionParser.parse("Shop\nTổng tiền 50.000\nThanh toán 60.000").amount);
        assertEquals(0, TransactionParser.parse("Shop\nLatte 95.000\n0909123456").amount);
    }

    @Test
    public void repeatedSameTotalIsNotAnAmbiguity() {
        assertEquals(95_000, TransactionParser.parse("Shop\nTổng tiền 95.000\nThanh toán 95.000").amount);
    }

    @Test
    public void multipleNumbersOnTotalLineRequireManualReview() {
        assertEquals(0, TransactionParser.parse("Shop\nTOTAL 95.000 VAT 10%").amount);
    }

    @Test
    public void emptyAndMalformedInputNeverProduceFinancialData() {
        assertEquals(0, TransactionParser.parse(null).amount);
        assertEquals("", TransactionParser.parse("").payee);
        assertEquals(0, TransactionParser.parse("TOTAL 9.50").amount);
    }

    // === Các bài test bổ sung cho tính năng OCR hoàn chỉnh ===

    @Test
    public void extractsDateAndPredictsFoodCategoryFromCoffeeReceipt() {
        String receipt = "Highlands Coffee\n" +
                "ĐT: 028 1234 5678\n" +
                "Ngày: 26/10/2026 15:45\n" +
                "1 Cà phê sữa đá\n" +
                "TỔNG CỘNG: 65.000 đ\n" +
                "Cảm ơn quý khách";

        TransactionParser.Draft draft = TransactionParser.parse(receipt);
        assertEquals(65_000, draft.amount);
        assertEquals("Highlands Coffee", draft.payee);
        assertEquals("food", draft.categoryHint);
        assertNotNull(draft.date);

        Calendar cal = Calendar.getInstance(FinanceBook.ZONE);
        cal.setTimeInMillis(draft.date);
        assertEquals(2026, cal.get(Calendar.YEAR));
        assertEquals(Calendar.OCTOBER, cal.get(Calendar.MONTH));
        assertEquals(26, cal.get(Calendar.DAY_OF_MONTH));
        assertEquals(15, cal.get(Calendar.HOUR_OF_DAY));
        assertEquals(45, cal.get(Calendar.MINUTE));

        assertFalse(draft.amountUncertain);
        assertFalse(draft.payeeUncertain);
        assertFalse(draft.dateUncertain);
        assertFalse(draft.categoryUncertain);
    }

    @Test
    public void extractsTransportCategoryAndDateFromGrabReceipt() {
        String receipt = "GrabCar\n" +
                "Chuyến đi ngày 14-11-2026 08:30\n" +
                "Tài xế: Nguyễn Văn A\n" +
                "TIỀN THANH TOÁN: 85.000 VND";

        TransactionParser.Draft draft = TransactionParser.parse(receipt);
        assertEquals(85_000, draft.amount);
        assertEquals("transport", draft.categoryHint);
        assertNotNull(draft.date);

        Calendar cal = Calendar.getInstance(FinanceBook.ZONE);
        cal.setTimeInMillis(draft.date);
        assertEquals(2026, cal.get(Calendar.YEAR));
        assertEquals(Calendar.NOVEMBER, cal.get(Calendar.MONTH));
        assertEquals(14, cal.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void extractsShoppingCategoryFromSupermarketReceiptWithBoilerplateHeader() {
        String receipt = "CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM\n" +
                "HÓA ĐƠN BÁN HÀNG\n" +
                "WinMart+\n" +
                "Ngày: 05/11/2026\n" +
                "Bánh quy Cosy 45.000\n" +
                "Sữa tươi Vinamilk 35.000\n" +
                "Tổng tiền thanh toán: 80.000";

        TransactionParser.Draft draft = TransactionParser.parse(receipt);
        assertEquals(80_000, draft.amount);
        assertEquals("WinMart+", draft.payee);
        assertEquals("shopping", draft.categoryHint);
        assertNotNull(draft.date);
    }

    @Test
    public void extractsBillsCategoryFromUtilityBill() {
        String receipt = "TỔNG CÔNG TY ĐIỆN LỰC EVN HANOI\n" +
                "Mã KH: PE0123456789\n" +
                "Ngày lập: 2026-11-10\n" +
                "Tiền điện kỳ 10/2026\n" +
                "Số tiền thanh toán: 520.000 đ";

        TransactionParser.Draft draft = TransactionParser.parse(receipt);
        assertEquals(520_000, draft.amount);
        assertEquals("bills", draft.categoryHint);
        assertNotNull(draft.date);
    }

    @Test
    public void supportsColumnarTotalWhereAmountIsOnNextLine() {
        String receipt = "Nhà hàng Sen Vàng\n" +
                "Lẩu hải sản\n" +
                "TỔNG CỘNG\n" +
                "350.000";

        TransactionParser.Draft draft = TransactionParser.parse(receipt);
        assertEquals(350_000, draft.amount);
        assertEquals("food", draft.categoryHint);
    }

    @Test
    public void uncertainFlagsProperlyMarkMissingOrAmbiguousFields() {
        String receipt = "Chỉ có chữ không có số tiền và ngày tháng";
        TransactionParser.Draft draft = TransactionParser.parse(receipt);
        assertTrue(draft.amountUncertain);
        assertTrue(draft.dateUncertain);
        assertEquals(0, draft.amount);
        assertNull(draft.date);
    }

    // === Các bài test trích xuất phương thức / Ví thanh toán (Wallet Prediction) ===

    @Test
    public void extractsCashWalletHintWhenPaidByCash() {
        String receipt = "Highlands Coffee\n" +
                "Ngày: 26/10/2026 15:45\n" +
                "TỔNG CỘNG: 65.000 đ\n" +
                "Hình thức TT: Tiền mặt\n" +
                "Tiền khách đưa: 100.000 đ\n" +
                "Tiền thừa: 35.000 đ";

        TransactionParser.Draft draft = TransactionParser.parse(receipt);
        assertEquals("cash", draft.walletHint);
    }

    @Test
    public void extractsBankWalletHintWhenPaidByCardOrTransfer() {
        String receipt = "Nhà hàng Pizza 4P's\n" +
                "Ngày: 10/11/2026 19:30\n" +
                "Tổng tiền: 450.000 VND\n" +
                "Phương thức thanh toán: Quẹt thẻ POS (Thẻ VISA)";

        TransactionParser.Draft draft = TransactionParser.parse(receipt);
        assertEquals("bank", draft.walletHint);
    }

    @Test
    public void extractsSpecificBankOrEWalletHintWhenBrandMentioned() {
        String receiptMomo = "Cửa hàng tiện lợi Circle K\n" +
                "Ngày: 01/11/2026 12:00\n" +
                "Tổng cộng: 55.000 đ\n" +
                "Thanh toán qua ví điện tử MoMo";
        assertEquals("momo", TransactionParser.parse(receiptMomo).walletHint);

        String receiptTechcombank = "Quán cơm Tấm Sài Gòn\n" +
                "Tổng thanh toán: 40.000 đ\n" +
                "Hình thức: Chuyển khoản Techcombank";
        assertEquals("techcombank", TransactionParser.parse(receiptTechcombank).walletHint);
    }
}
