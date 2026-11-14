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
