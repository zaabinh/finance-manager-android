package com.example.fintrack.app.chatbot.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.fintrack.app.chatbot.domain.ChatMessage;
import com.example.fintrack.app.chatbot.domain.FinancialChatbotEngine;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Quản lý lưu trữ và truy xuất lịch sử cuộc trò chuyện với Chatbot.
 */
public class ChatbotRepository {

    public interface Storage {
        String getString(String key, String defValue);
        void putString(String key, String value);
    }

    private static class SharedPrefsStorage implements Storage {
        private final SharedPreferences prefs;

        SharedPrefsStorage(Context context) {
            this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }

        @Override
        public String getString(String key, String defValue) {
            return prefs.getString(key, defValue);
        }

        @Override
        public void putString(String key, String value) {
            prefs.edit().putString(key, value).apply();
        }
    }

    private static final String PREF_NAME = "fintrack_chatbot_prefs";
    private static final String KEY_MESSAGES = "chat_messages";
    private static final int MAX_MESSAGES_SAVED = 100;

    private final Storage storage;
    private final Gson gson;

    public ChatbotRepository(Context context) {
        this(new SharedPrefsStorage(context));
    }

    public ChatbotRepository(Storage storage) {
        this.storage = storage;
        this.gson = new Gson();
    }

    public synchronized List<ChatMessage> getMessages() {
        String json = storage.getString(KEY_MESSAGES, null);
        if (json == null || json.trim().isEmpty()) {
            List<ChatMessage> initial = new ArrayList<>();
            initial.add(createInitialWelcomeMessage());
            saveMessages(initial);
            return initial;
        }

        try {
            Type listType = new TypeToken<ArrayList<ChatMessage>>() {}.getType();
            List<ChatMessage> list = gson.fromJson(json, listType);
            if (list == null || list.isEmpty()) {
                list = new ArrayList<>();
                list.add(createInitialWelcomeMessage());
                saveMessages(list);
            }
            return Collections.unmodifiableList(list);
        } catch (Exception e) {
            List<ChatMessage> initial = new ArrayList<>();
            initial.add(createInitialWelcomeMessage());
            return initial;
        }
    }

    public synchronized void addMessage(ChatMessage message) {
        if (message == null) return;
        List<ChatMessage> list = new ArrayList<>(getMessages());
        list.add(message);
        if (list.size() > MAX_MESSAGES_SAVED) {
            list = new ArrayList<>(list.subList(list.size() - MAX_MESSAGES_SAVED, list.size()));
        }
        saveMessages(list);
    }

    public synchronized void clearMessages() {
        List<ChatMessage> initial = new ArrayList<>();
        initial.add(createInitialWelcomeMessage());
        saveMessages(initial);
    }

    private void saveMessages(List<ChatMessage> list) {
        String json = gson.toJson(list);
        storage.putString(KEY_MESSAGES, json);
    }

    public static ChatMessage createInitialWelcomeMessage() {
        String welcome = "Chào bạn! Tôi là <b>Đồng Bot</b> — trợ lý tài chính thông minh của bạn. 🌿<br/><br/>"
                + "Tôi có thể hỗ trợ bạn:<br/>"
                + "• <b>Ghi chép giao dịch tự nhiên:</b> ví dụ <i>'vừa ăn trưa 45k'</i> hoặc <i>'thu lương 15 triệu'</i><br/>"
                + "• <b>Kiểm tra tình hình tài chính:</b> số dư ví, ngân sách tháng<br/>"
                + "• <b>Tư vấn tài chính:</b> quy tắc 50/30/20, quỹ khẩn cấp, mẹo tiết kiệm thông minh<br/><br/>"
                + "Hôm nay bạn muốn tôi hỗ trợ điều gì nào?";

        return ChatMessage.botMessage(welcome, null, FinancialChatbotEngine.defaultPrompts());
    }
}
