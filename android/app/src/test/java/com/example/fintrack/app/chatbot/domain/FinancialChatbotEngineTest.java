package com.example.fintrack.app.chatbot.domain;

import com.example.fintrack.app.finance.domain.FinanceBook;
import com.example.fintrack.app.finance.domain.TransactionType;
import com.example.fintrack.app.finance.domain.Wallet;
import com.example.fintrack.app.finance.domain.WalletType;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FinancialChatbotEngineTest {

    @Test
    public void process_naturalLanguageExpense_createsTransactionAction() {
        ChatMessage message = FinancialChatbotEngine.process("Vừa ăn trưa 45k", null);

        assertNotNull(message);
        assertEquals(ChatMessage.Sender.BOT, message.sender);
        assertNotNull(message.action);
        assertEquals(ChatMessage.ActionType.CREATE_TRANSACTION, message.action.type);
        assertEquals(45_000L, message.action.amount);
        assertEquals(TransactionType.EXPENSE, message.action.transactionType);
        assertTrue(message.content.contains("45.000"));
        assertTrue(message.content.toLowerCase().contains("ăn trưa"));
    }

    @Test
    public void process_naturalLanguageIncome_createsIncomeAction() {
        ChatMessage message = FinancialChatbotEngine.process("Nhận lương 15 triệu", null);

        assertNotNull(message);
        assertEquals(ChatMessage.Sender.BOT, message.sender);
        assertNotNull(message.action);
        assertEquals(ChatMessage.ActionType.CREATE_TRANSACTION, message.action.type);
        assertEquals(15_000_000L, message.action.amount);
        assertEquals(TransactionType.INCOME, message.action.transactionType);
        assertTrue(message.content.toLowerCase().contains("lương"));
    }

    @Test
    public void process_50_30_20RuleQuery_returnsDetailedExplanation() {
        ChatMessage message = FinancialChatbotEngine.process("Quy tắc 50/30/20 là gì?", null);

        assertNotNull(message);
        assertEquals(ChatMessage.Sender.BOT, message.sender);
        assertTrue(message.content.contains("50%"));
        assertTrue(message.content.contains("30%"));
        assertTrue(message.content.contains("20%"));
    }

    @Test
    public void process_emergencyFundQuery_returnsStandardGuidance() {
        ChatMessage message = FinancialChatbotEngine.process("Làm sao để lập quỹ khẩn cấp?", null);

        assertNotNull(message);
        assertEquals(ChatMessage.Sender.BOT, message.sender);
        assertTrue(message.content.contains("3 đến 6 tháng"));
    }

    @Test
    public void process_savingTipsQuery_returnsPracticalTips() {
        ChatMessage message = FinancialChatbotEngine.process("Cho tôi vài mẹo tiết kiệm tiền", null);

        assertNotNull(message);
        assertEquals(ChatMessage.Sender.BOT, message.sender);
        assertTrue(message.content.toLowerCase().contains("tiết kiệm"));
        assertTrue(message.content.contains("24"));
    }

    @Test
    public void process_debtAdviceQuery_returnsStrategies() {
        ChatMessage message = FinancialChatbotEngine.process("Tôi muốn trả nợ hiệu quả", null);

        assertNotNull(message);
        assertEquals(ChatMessage.Sender.BOT, message.sender);
        assertTrue(message.content.toLowerCase().contains("tuyết"));
    }

    @Test
    public void process_balanceQuery_withFinanceBook_returnsAccurateData() {
        FinanceBook book = new FinanceBook(FinanceBook.empty());
        book.addWallet("Ví tiền mặt", 2_000_000L, WalletType.CASH);

        ChatMessage message = FinancialChatbotEngine.process("Số dư trong ví còn bao nhiêu?", book);

        assertNotNull(message);
        assertEquals(ChatMessage.Sender.BOT, message.sender);
        assertTrue(message.content.contains("2.000.000"));
        assertTrue(message.content.contains("Ví tiền mặt"));
    }

    @Test
    public void process_budgetQuery_withFinanceBook_returnsBudgetGuidance() {
        FinanceBook book = new FinanceBook(FinanceBook.empty());

        ChatMessage message = FinancialChatbotEngine.process("Tình hình ngân sách tháng này?", book);

        assertNotNull(message);
        assertEquals(ChatMessage.Sender.BOT, message.sender);
        assertTrue(message.content.toLowerCase().contains("ngân sách"));
    }

    @Test
    public void process_greetingsAndThanks_returnsFriendlyResponses() {
        ChatMessage greeting = FinancialChatbotEngine.process("Chào bạn", null);
        assertNotNull(greeting);
        assertTrue(greeting.content.toLowerCase().contains("đồng bot"));

        ChatMessage thanks = FinancialChatbotEngine.process("Cảm ơn bạn rất nhiều", null);
        assertNotNull(thanks);
        assertTrue(thanks.content.toLowerCase().contains("hỗ trợ"));
    }

    @Test
    public void process_emptyOrNull_returnsSafePrompts() {
        ChatMessage emptyResult = FinancialChatbotEngine.process("", null);
        assertNotNull(emptyResult);
        assertNull(emptyResult.action);
        assertTrue(emptyResult.promptSuggestions.size() > 0);

        ChatMessage nullResult = FinancialChatbotEngine.process(null, null);
        assertNotNull(nullResult);
        assertNull(nullResult.action);
    }
}
