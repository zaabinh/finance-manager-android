package com.example.fintrack.app.notification.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.fintrack.app.notification.model.NotificationItem;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DemoNotificationProviderTest {
    private final Map<String, Set<String>> saved = new HashMap<>();
    private final DemoNotificationProvider.ReadStore store = new DemoNotificationProvider.ReadStore() {
        @Override public Set<String> readIds(String accountKey) { return saved.getOrDefault(accountKey, new HashSet<>()); }
        @Override public void saveReadIds(String accountKey, Set<String> ids) { saved.put(accountKey, new HashSet<>(ids)); }
    };
    private String user = "user-a";
    private final DemoNotificationProvider provider = new DemoNotificationProvider(() -> user, store);

    private boolean read(String id) {
        for (NotificationItem item : provider.notifications()) if (item.getId().equals(id)) return item.isRead();
        throw new AssertionError(id);
    }

    @Test
    public void freshAccountStartsUnreadSoTheOldGlobalFlagIsNotApplied() {
        assertEquals(3, provider.notifications().size());
        for (NotificationItem item : provider.notifications()) assertFalse(item.isRead());
    }

    @Test
    public void markingOneIdLeavesTheOthersUnread() {
        provider.markRead("n2");
        assertTrue(read("n2"));
        assertFalse(read("n1"));
        assertFalse(read("n3"));
    }

    @Test
    public void readStateIsPerAccount() {
        provider.markAllRead();
        assertTrue(read("n1")); assertTrue(read("n2")); assertTrue(read("n3"));

        user = "user-b";
        for (NotificationItem item : provider.notifications()) assertFalse(item.isRead());
        provider.markRead("n1");

        user = "user-a";
        assertTrue(read("n3"));
        user = "user-b";
        assertTrue(read("n1")); assertFalse(read("n3"));
    }

    @Test
    public void rawUserIdIsNeverAStorageKey() {
        provider.markRead("n1");
        assertEquals(1, saved.size());
        assertFalse(saved.keySet().iterator().next().contains("user-a"));
    }

    @Test
    public void withoutAnAccountNothingIsReadOrSaved() {
        user = null;
        provider.markAllRead();
        assertTrue(saved.isEmpty());
        for (NotificationItem item : provider.notifications()) assertFalse(item.isRead());
    }
}
