package com.example.fintrack.app.voice.domain;

import com.example.fintrack.app.finance.domain.TransactionType;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

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
    public void parse_naturalDateAndPaymentMethod_extractsDateAndWalletHint() {
        VoiceTransactionParser.ParsedResult yesterday = VoiceTransactionParser.parse("Hôm qua ăn tối 150k");
        assertEquals(150_000L, yesterday.amount);
        assertEquals("Ăn tối", yesterday.note);
        assertTrue(yesterday.occurredAt < System.currentTimeMillis() - 80000000L);

        VoiceTransactionParser.ParsedResult card = VoiceTransactionParser.parse("Quẹt thẻ 350k siêu thị");
        assertEquals(350_000L, card.amount);
        assertEquals("Siêu thị", card.note);
        assertEquals("thẻ", card.sourceWalletHint);

        VoiceTransactionParser.ParsedResult momo = VoiceTransactionParser.parse("Bắn momo 45k trà sữa");
        assertEquals(45_000L, momo.amount);
        assertEquals("Trà sữa", momo.note);
        assertEquals("momo", momo.sourceWalletHint);
    }

    @Test
    public void parse_slangAndSpokenNumerals_extractsAccurately() {
        // Chai (1 chai = 1M, 2 chai rưỡi = 2.5M, nửa chai = 500k)
        VoiceTransactionParser.ParsedResult chai = VoiceTransactionParser.parse("Mua bàn làm việc 2 chai");
        assertEquals(2_000_000L, chai.amount);
        assertEquals("Mua bàn làm việc", chai.note);

        VoiceTransactionParser.ParsedResult nuaChai = VoiceTransactionParser.parse("Nửa chai tiền điện");
        assertEquals(500_000L, nuaChai.amount);
        assertEquals("Điện", nuaChai.note);

        // Lốp, vé, sọi
        VoiceTransactionParser.ParsedResult lop = VoiceTransactionParser.parse("Tiêu 2 lốp mua sách");
        assertEquals(200_000L, lop.amount);
        assertEquals("Mua sách", lop.note);

        VoiceTransactionParser.ParsedResult ve = VoiceTransactionParser.parse("Vé xe 2 vé");
        assertEquals(200_000L, ve.amount);

        VoiceTransactionParser.ParsedResult soi = VoiceTransactionParser.parse("Trà đá 3 sọi");
        assertEquals(30_000L, soi.amount);

        // Trăm chục (2 trăm 3 = 230k, trăm hai = 120k)
        VoiceTransactionParser.ParsedResult tramChuc = VoiceTransactionParser.parse("Ăn lẩu 2 trăm 3");
        assertEquals(230_000L, tramChuc.amount);
        assertEquals("Ăn lẩu", tramChuc.note);

        VoiceTransactionParser.ParsedResult tramHai = VoiceTransactionParser.parse("Tiền chợ trăm hai");
        assertEquals(120_000L, tramHai.amount);
        assertEquals("Chợ", tramHai.note);

        // Phát âm hăm lăm, năm nhăm
        VoiceTransactionParser.ParsedResult hamLam = VoiceTransactionParser.parse("Cà phê hăm lăm k");
        assertEquals(25_000L, hamLam.amount);
        assertEquals("Cà phê", hamLam.note);

        VoiceTransactionParser.ParsedResult namNham = VoiceTransactionParser.parse("Ăn phở năm nhăm k");
        assertEquals(55_000L, namNham.amount);
        assertEquals("Ăn phở", namNham.note);
    }

    @Test
    public void parseBest_selectsCandidateWithAmount() {
        // Giả sử câu phỏng đoán đầu tiên của Google STT bị thiếu số tiền (chỉ nhận diện "ăn tối")
        // nhưng câu phỏng đoán thứ hai lại nhận diện đúng "ăn tối 120k"
        VoiceTransactionParser.ParsedResult best = VoiceTransactionParser.parseBest(
                Arrays.asList("ăn tối", "ăn tối 120k", "an toi 120")
        );
        assertEquals(120_000L, best.amount);
        assertEquals("Ăn tối", best.note);

        // Trường hợp tất cả câu đều thiếu tiền: trả về candidate đầu tiên an toàn với amount = 0
        VoiceTransactionParser.ParsedResult missing = VoiceTransactionParser.parseBest(
                Arrays.asList("uống bia", "uong bia")
        );
        assertEquals(0L, missing.amount);
        assertEquals("Uống bia", missing.note);
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

    @Test
    public void mergeSupplement_amountMissing_supplementsAmountAndPreservesNote() {
        // Lượt 1: Người dùng chỉ nói "Ăn sáng" (thiếu số tiền)
        VoiceTransactionParser.ParsedResult base = VoiceTransactionParser.parse("Ăn sáng");
        assertEquals(0L, base.amount);
        assertEquals("Ăn sáng", base.note);

        // Lượt 2: Người dùng nói bổ sung "35 nghìn"
        VoiceTransactionParser.ParsedResult merged = VoiceTransactionParser.mergeSupplement(
                base,
                Arrays.asList("35 nghìn", "35k", "ba mươi lăm nghìn")
        );

        assertEquals(35_000L, merged.amount);
        assertEquals("Ăn sáng", merged.note);
        assertEquals(TransactionType.EXPENSE, merged.type);
    }

    @Test
    public void mergeSupplement_genericNote_supplementsNoteAndPreservesAmount() {
        // Lượt 1: Người dùng chỉ nói "Chi 50k" (thiếu nội dung chi tiết)
        VoiceTransactionParser.ParsedResult base = VoiceTransactionParser.parse("Chi 50k");
        assertEquals(50_000L, base.amount);
        assertEquals("Chi tiêu", base.note);

        // Lượt 2: Người dùng nói bổ sung "Cà phê sáng"
        VoiceTransactionParser.ParsedResult merged = VoiceTransactionParser.mergeSupplement(
                base,
                Collections.singletonList("Cà phê sáng")
        );

        assertEquals(50_000L, merged.amount);
        assertEquals("Cà phê sáng", merged.note);
    }

    @Test
    public void mergeSupplement_transfer_supplementsDestinationWallet() {
        // Lượt 1: Người dùng nói "Chuyển khoản 500k từ VCB" (thiếu ví đích)
        VoiceTransactionParser.ParsedResult base = VoiceTransactionParser.parse("Chuyển khoản 500k từ VCB");
        assertEquals(TransactionType.TRANSFER, base.type);
        assertEquals(500_000L, base.amount);
        assertEquals("", base.destinationWalletHint);

        // Lượt 2: Người dùng nói bổ sung "Sang ví MoMo"
        VoiceTransactionParser.ParsedResult merged = VoiceTransactionParser.mergeSupplement(
                base,
                Collections.singletonList("Sang ví MoMo")
        );

        assertEquals(500_000L, merged.amount);
        assertEquals("MoMo", merged.destinationWalletHint);
    }
}
