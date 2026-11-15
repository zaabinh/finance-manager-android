package com.example.fintrack.app.chatbot.data;

import com.example.fintrack.app.chatbot.domain.ChatMessage;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ChatbotRepositoryTest {

    private static class FakeStorage implements ChatbotRepository.Storage {
        final Map<String, String> data = new HashMap<>();

        @Override
        public String getString(String key, String defValue) {
            return data.getOrDefault(key, defValue);
        }

        @Override
        public void putString(String key, String value) {
            data.put(key, value);
        }
    }

    private FakeStorage storage;
    private ChatbotRepository repository;

    @Before
    public void setUp() {
        storage = new FakeStorage();
        repository = new ChatbotRepository(storage);
    }

    @Test
    public void getMessages_initially_returnsWelcomeMessage() {
        List<ChatMessage> messages = repository.getMessages();

        assertNotNull(messages);
        assertEquals(1, messages.size());
        assertEquals(ChatMessage.Sender.BOT, messages.get(0).sender);
        assertTrue(messages.get(0).content.contains("Đồng Bot"));
    }

    @Test
    public void addMessage_appendsAndPersistsMessage() {
        ChatMessage userMsg = ChatMessage.userMessage("Hỏi về ngân sách");
        repository.addMessage(userMsg);

        List<ChatMessage> list = repository.getMessages();
        assertEquals(2, list.size());
        assertEquals("Hỏi về ngân sách", list.get(1).content);
        assertEquals(ChatMessage.Sender.USER, list.get(1).sender);
    }

    @Test
    public void clearMessages_resetsToWelcomeMessage() {
        repository.addMessage(ChatMessage.userMessage("Tin nhắn 1"));
        repository.addMessage(ChatMessage.botMessage("Tin nhắn 2"));
        assertEquals(3, repository.getMessages().size());

        repository.clearMessages();

        List<ChatMessage> list = repository.getMessages();
        assertEquals(1, list.size());
        assertTrue(list.get(0).content.contains("Đồng Bot"));
    }

    @Test
    public void addMessage_limitsMaximumSavedMessages() {
        for (int i = 0; i < 120; i++) {
            repository.addMessage(ChatMessage.userMessage("Msg " + i));
        }

        List<ChatMessage> list = repository.getMessages();
        assertTrue(list.size() <= 100);
    }
}
