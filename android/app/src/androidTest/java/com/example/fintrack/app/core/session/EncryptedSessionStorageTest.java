package com.example.fintrack.app.core.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Runs against the real encrypted file; the state found on the device is put back afterwards. */
@RunWith(AndroidJUnit4.class)
public class EncryptedSessionStorageTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private SessionSnapshot original;

    @Before
    public void remember() {
        original = new EncryptedSessionStorage(context).load();
    }

    @After
    public void restore() {
        new EncryptedSessionStorage(context).save(original);
    }

    @Test
    public void savedSessionIsReadBackByANewInstance() {
        new EncryptedSessionStorage(context).save(new SessionSnapshot(41, true, "access", "refresh", "user-1", "user@example.com", "Minh Anh", true));

        SessionSnapshot loaded = new EncryptedSessionStorage(context).load();

        assertEquals(41, loaded.generation);
        assertTrue(loaded.loggedIn);
        assertEquals("access", loaded.accessToken);
        assertEquals("refresh", loaded.refreshToken);
        assertEquals("user-1", loaded.userId);
        assertEquals("user@example.com", loaded.email);
        assertEquals("Minh Anh", loaded.name);
        assertTrue(loaded.setupCompleted);
    }

    @Test
    public void storeGenerationIsPersistedAcrossRestarts() {
        SessionStore store = new SessionStore(new EncryptedSessionStorage(context));
        SessionSnapshot session = store.login("access", "refresh", false);

        SessionStore restarted = new SessionStore(new EncryptedSessionStorage(context));

        assertEquals(session.generation, restarted.current().generation);
        assertTrue(restarted.isCurrent(session));
        assertNull(restarted.current().userId);
        assertTrue(restarted.login("access-2", "refresh-2", false).generation > session.generation);
    }

    @Test
    public void clearRemovesTheSessionButKeepsTheSetupFlag() {
        SessionStore store = new SessionStore(new EncryptedSessionStorage(context));
        SessionSnapshot session = store.login("access", "refresh", true);
        store.updateProfile(session, "user-1", "user@example.com", "Minh Anh");

        assertTrue(store.clear(session));

        SessionSnapshot loaded = new EncryptedSessionStorage(context).load();
        assertFalse(loaded.loggedIn);
        assertNull(loaded.accessToken);
        assertNull(loaded.refreshToken);
        assertNull(loaded.userId);
        assertNull(loaded.email);
        assertNull(loaded.name);
        assertTrue(loaded.setupCompleted);
        assertTrue(loaded.generation > session.generation);
    }
}
