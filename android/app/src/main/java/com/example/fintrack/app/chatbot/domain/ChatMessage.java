package com.example.fintrack.app.chatbot.domain;

import com.example.fintrack.app.finance.domain.TransactionType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Model biểu diễn một tin nhắn trong cuộc hội thoại với Chatbot.
 */
public class ChatMessage {

    public enum Sender {
        USER,
        BOT,
        SYSTEM
    }

    public enum ActionType {
        CREATE_TRANSACTION,
        VIEW_BUDGET,
        VIEW_ANALYTICS,
        VIEW_WALLETS
    }

    public static class ChatAction {
        public final ActionType type;
        public final String label;
        public final long amount;
        public final TransactionType transactionType;
        public final String note;
        public final String categoryName;

        public ChatAction(ActionType type, String label, long amount, TransactionType transactionType, String note, String categoryName) {
            this.type = type;
            this.label = label != null ? label : "";
            this.amount = amount;
            this.transactionType = transactionType != null ? transactionType : TransactionType.EXPENSE;
            this.note = note != null ? note : "";
            this.categoryName = categoryName != null ? categoryName : "";
        }

        public static ChatAction createTransaction(long amount, TransactionType type, String note, String categoryName) {
            String label = (type == TransactionType.INCOME ? "Lưu thu nhập: " : "Lưu chi tiêu: ")
                    + String.format("%,d đ", amount).replace(',', '.');
            return new ChatAction(ActionType.CREATE_TRANSACTION, label, amount, type, note, categoryName);
        }

        public static ChatAction viewBudget() {
            return new ChatAction(ActionType.VIEW_BUDGET, "Xem chi tiết ngân sách", 0, null, "", "");
        }

        public static ChatAction viewAnalytics() {
            return new ChatAction(ActionType.VIEW_ANALYTICS, "Mở báo cáo thu chi", 0, null, "", "");
        }
    }

    public final String id;
    public final Sender sender;
    public final String content;
    public final long timestamp;
    public final ChatAction action;
    public final List<String> promptSuggestions;

    public ChatMessage(String id, Sender sender, String content, long timestamp, ChatAction action, List<String> promptSuggestions) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.sender = sender != null ? sender : Sender.BOT;
        this.content = content != null ? content : "";
        this.timestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
        this.action = action;
        this.promptSuggestions = promptSuggestions != null ? Collections.unmodifiableList(promptSuggestions) : Collections.emptyList();
    }

    public static ChatMessage userMessage(String text) {
        return new ChatMessage(UUID.randomUUID().toString(), Sender.USER, text, System.currentTimeMillis(), null, null);
    }

    public static ChatMessage botMessage(String text) {
        return new ChatMessage(UUID.randomUUID().toString(), Sender.BOT, text, System.currentTimeMillis(), null, null);
    }

    public static ChatMessage botMessage(String text, ChatAction action, List<String> suggestions) {
        return new ChatMessage(UUID.randomUUID().toString(), Sender.BOT, text, System.currentTimeMillis(), action, suggestions);
    }
}
