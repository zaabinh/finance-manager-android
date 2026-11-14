package com.example.fintrack.app.voice.domain;

import com.example.fintrack.app.finance.domain.TransactionType;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class VoiceTransactionParserTest {

    @Test
    public void parse_expenseWithNghin_extractsAmountAndNote() {
        VoiceTransactionParser.ParsedResult result = VoiceTransactionParser.parse("Ăn trưa 45 nghìn");
        assertNotNull(result);
        assertEquals(TransactionType.EXPENSE, result.type);
        assertEquals(45_000L, result.amount);
        assertTrue(result.note.contains("Ăn trưa"));
    }

    @Test
    public void parse_expenseWithK_extractsAmountAndNote() {
        VoiceTransactionParser.ParsedResult result = VoiceTransactionParser.parse("Chi 50k cà phê");
        assertNotNull(result);
        assertEquals(TransactionType.EXPENSE, result.type);
        assertEquals(50_000L, result.amount);
        assertTrue(result.note.toLowerCase().contains("cà phê"));
    }

    @Test
    public void parse_incomeWithTrieu_extractsIncomeAndMillions() {
        VoiceTransactionParser.ParsedResult result = VoiceTransactionParser.parse("Nhận lương tháng 11 15 triệu");
        assertNotNull(result);
        assertEquals(TransactionType.INCOME, result.type);
        assertEquals(15_000_000L, result.amount);
        assertTrue(result.note.toLowerCase().contains("lương"));
    }

    @Test
    public void parse_decimalTrieu_extractsCorrectValue() {
        VoiceTransactionParser.ParsedResult result = VoiceTransactionParser.parse("Thưởng dự án 2.5 triệu");
        assertNotNull(result);
        assertEquals(TransactionType.INCOME, result.type);
        assertEquals(2_500_000L, result.amount);

        VoiceTransactionParser.ParsedResult resultComma = VoiceTransactionParser.parse("Thưởng dự án 2,5 triệu");
        assertNotNull(resultComma);
        assertEquals(2_500_000L, resultComma.amount);
    }

    @Test
    public void parse_slangTrieuRuoiAndCuRuoi_addsHalfMillion() {
        VoiceTransactionParser.ParsedResult result1 = VoiceTransactionParser.parse("Mua áo 1 triệu rưỡi");
        assertEquals(1_500_000L, result1.amount);
        assertTrue(result1.note.toLowerCase().contains("mua áo"));

        VoiceTransactionParser.ParsedResult result2 = VoiceTransactionParser.parse("2 củ rưỡi tiền phòng");
        assertEquals(2_500_000L, result2.amount);
        assertTrue(result2.note.toLowerCase().contains("phòng"));
    }

    @Test
    public void parse_slangLitAndChuc_extractsSlangAmounts() {
        VoiceTransactionParser.ParsedResult lit = VoiceTransactionParser.parse("Sửa xe 2 lít");
        assertEquals(200_000L, lit.amount);
        assertTrue(lit.note.toLowerCase().contains("sửa xe"));

        VoiceTransactionParser.ParsedResult chuc = VoiceTransactionParser.parse("Mua đồ 5 chục");
        assertEquals(50_000L, chuc.amount);
        assertTrue(chuc.note.toLowerCase().contains("mua đồ"));

        VoiceTransactionParser.ParsedResult tramRuoi = VoiceTransactionParser.parse("Trăm rưỡi tiền xăng");
        assertEquals(150_000L, tramRuoi.amount);
        assertTrue(tramRuoi.note.toLowerCase().contains("xăng"));
    }

    @Test
    public void parse_billion_extractsCorrectBillions() {
        VoiceTransactionParser.ParsedResult ty = VoiceTransactionParser.parse("Bán xe được 1.2 tỷ");
        assertEquals(TransactionType.INCOME, ty.type);
        assertEquals(1_200_000_000L, ty.amount);
        assertTrue(ty.note.toLowerCase().contains("bán xe"));
    }

    @Test
    public void parse_wordsAmounts_extractsCorrectValue() {
        VoiceTransactionParser.ParsedResult bonLam = VoiceTransactionParser.parse("Bốn mươi lăm nghìn tiền cà phê");
        assertEquals(45_000L, bonLam.amount);

        VoiceTransactionParser.ParsedResult namMuoi = VoiceTransactionParser.parse("Năm mươi nghìn ăn sáng");
        assertEquals(50_000L, namMuoi.amount);

        VoiceTransactionParser.ParsedResult haiTrieuRuoi = VoiceTransactionParser.parse("Hai triệu rưỡi mua máy tính");
        assertEquals(2_500_000L, haiTrieuRuoi.amount);

        VoiceTransactionParser.ParsedResult tramRuoi = VoiceTransactionParser.parse("Một trăm năm mươi nghìn tiền ăn");
        assertEquals(150_000L, tramRuoi.amount);
    }

    @Test
    public void parse_transferWithDestinationHint_extractsTransferAndHint() {
        VoiceTransactionParser.ParsedResult result = VoiceTransactionParser.parse("Chuyển khoản 200k cho Nam");
        assertEquals(TransactionType.TRANSFER, result.type);
        assertEquals(200_000L, result.amount);

        VoiceTransactionParser.ParsedResult transferWithDest = VoiceTransactionParser.parse("Bắn 500 nghìn sang ví mẹ");
        assertEquals(TransactionType.TRANSFER, transferWithDest.type);
        assertEquals(500_000L, transferWithDest.amount);
        assertEquals("mẹ", transferWithDest.destinationWalletHint);
    }

    @Test
    public void parse_formattedDigitsWithCurrencySuffix_removesCurrencyCleanly() {
        VoiceTransactionParser.ParsedResult result1 = VoiceTransactionParser.parse("Mua sách 120.000 đồng");
        assertEquals(120_000L, result1.amount);
        assertEquals("Mua sách", result1.note);

        VoiceTransactionParser.ParsedResult result2 = VoiceTransactionParser.parse("Đổ xăng 70 nghìn đồng");
        assertEquals(70_000L, result2.amount);
        assertEquals("Đổ xăng", result2.note);
    }

    @Test
    public void parse_emptyOrNull_returnsSafeDefault() {
        VoiceTransactionParser.ParsedResult emptyResult = VoiceTransactionParser.parse("");
        assertNotNull(emptyResult);
        assertEquals(0L, emptyResult.amount);

        VoiceTransactionParser.ParsedResult nullResult = VoiceTransactionParser.parse(null);
        assertNotNull(nullResult);
        assertEquals(0L, nullResult.amount);
    }
}
