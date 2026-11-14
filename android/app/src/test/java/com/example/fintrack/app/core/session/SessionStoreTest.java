package com.example.fintrack.app.core.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.fintrack.app.testing.FakeSessionStorage;

import org.junit.Test;

public class SessionStoreTest {
    private final FakeSessionStorage storage = new FakeSessionStorage();
    private final SessionStore store = new SessionStore(storage);

    private SessionSnapshot loginWithProfile(String token, String userId) {
        SessionSnapshot session = store.login(token, "refresh-" + token, true, true);
        assertTrue(store.updateProfile(session, userId, userId + "@example.com", "Name " + userId));
        return session;
    }

    @Test
    public void emptyStorageIsALoggedOutSession() {
        SessionSnapshot session = store.current();

        assertFalse(session.loggedIn);
        assertNull(session.accessToken);
        assertFalse(store.isCurrent(session));
        assertFalse(store.isCurrent(null));
        assertEquals(0, storage.saves);
    }

    @Test
    public void loginAlwaysStartsANewGenerationEvenForTheSameAccount() {
        SessionSnapshot first = loginWithProfile("a1", "user-1");
        SessionSnapshot second = store.login("a2", "r2", false, true);

        assertTrue(second.generation > first.generation);
        assertFalse(store.isCurrent(first));
        assertTrue(store.isCurrent(second));
        assertEquals("a2", store.current().accessToken);
        assertEquals("r2", store.current().refreshToken);
        assertFalse(store.current().setupCompleted);
        // Profile fields of the previous login are gone until the new login loads its own profile.
        assertNull(store.current().userId);
        assertNull(store.current().email);
        assertNull(store.current().name);

        SessionSnapshot sameAccountAgain = loginWithProfile("a3", "user-1");
        assertTrue(sameAccountAgain.generation > second.generation);
        assertFalse(store.isCurrent(second));
    }

    @Test
    public void tokenRefreshKeepsTheGenerationAndEverythingElse() {
        SessionSnapshot session = loginWithProfile("a1", "user-1");

        assertTrue(store.updateTokens(session, "a2", "r2"));

        SessionSnapshot now = store.current();
        assertEquals(session.generation, now.generation);
        assertEquals("a2", now.accessToken);
        assertEquals("r2", now.refreshToken);
        assertEquals("user-1", now.userId);
        assertTrue(now.setupCompleted);
        // A snapshot taken before the refresh still names the same login.
        assertTrue(store.isCurrent(session));
        assertTrue(store.updateProfile(session, "user-1", "new@example.com", "New name"));
        assertEquals("a2", store.current().accessToken);
        assertEquals("New name", store.current().name);
    }

    @Test
    public void completeSetupMarksTheCurrentSession() {
        SessionSnapshot session = store.login("a1", "r1", false, true);

        assertTrue(store.completeSetup(session));

        assertTrue(store.current().setupCompleted);
        assertEquals(session.generation, store.current().generation);
    }

    @Test
    public void everyConditionalWriteIsRefusedForAStaleSnapshot() {
        SessionSnapshot stale = loginWithProfile("a1", "user-1");
        store.login("a2", "r2", false, true); // same or another account: a new session either way
        SessionSnapshot current = store.current();
        int saves = storage.saves;

        assertFalse(store.updateTokens(stale, "evil", "evil"));
        assertFalse(store.updateProfile(stale, "user-1", "old@example.com", "Old"));
        assertFalse(store.completeSetup(stale));
        assertFalse(store.clear(stale));

        assertSame(current, store.current());
        assertEquals(saves, storage.saves);
    }

    @Test
    public void everyConditionalWriteIsRefusedAfterLogout() {
        SessionSnapshot session = loginWithProfile("a1", "user-1");
        assertTrue(store.clear(session));
        SessionSnapshot loggedOut = store.current();

        assertFalse(store.isCurrent(session));
        assertFalse(store.isCurrent(loggedOut));
        assertFalse(store.updateTokens(session, "a2", "r2"));
        assertFalse(store.updateProfile(session, "user-1", "e", "n"));
        assertFalse(store.completeSetup(session));
        assertFalse(store.clear(session));
        // The logged-out snapshot itself cannot be written to either.
        assertFalse(store.updateTokens(loggedOut, "a2", "r2"));
        assertFalse(store.clear(loggedOut));

        assertSame(loggedOut, store.current());
        assertFalse(store.current().loggedIn);
        assertNull(store.current().accessToken);
    }

    @Test
    public void clearRemovesTheSessionButKeepsTheSetupFlagAndChangesTheGeneration() {
        SessionSnapshot session = loginWithProfile("a1", "user-1");

        assertTrue(store.clear(session));

        SessionSnapshot now = store.current();
        assertFalse(now.loggedIn);
        assertNull(now.accessToken);
        assertNull(now.refreshToken);
        assertNull(now.userId);
        assertNull(now.email);
        assertNull(now.name);
        assertTrue(now.setupCompleted);
        assertTrue(now.generation > session.generation);
    }

    @Test
    public void stateAndGenerationSurviveARestart() {
        SessionSnapshot session = loginWithProfile("a1", "user-1");
        store.updateTokens(session, "a2", "r2");

        SessionStore restarted = new SessionStore(storage);

        SessionSnapshot restored = restarted.current();
        assertTrue(restored.loggedIn);
        assertEquals(session.generation, restored.generation);
        assertEquals("a2", restored.accessToken);
        assertEquals("r2", restored.refreshToken);
        assertEquals("user-1", restored.userId);
        assertEquals("user-1@example.com", restored.email);
        assertEquals("Name user-1", restored.name);
        assertTrue(restored.setupCompleted);
        // A snapshot from before the restart still names the same login.
        assertTrue(restarted.isCurrent(session));

        // Generations keep growing across restarts, also through a logout.
        assertTrue(restarted.clear(restored));
        SessionStore restartedAgain = new SessionStore(storage);
        assertFalse(restartedAgain.current().loggedIn);
        assertTrue(restartedAgain.current().setupCompleted);
        SessionSnapshot next = restartedAgain.login("a3", "r3", true, true);
        assertTrue(next.generation > restored.generation + 1);
        assertFalse(restartedAgain.isCurrent(session));
    }

    // --- "Ghi nhớ tôi" ---

    @Test
    public void loginWithoutRememberMeLastsForTheProcessAndEndsAtTheNextStart() {
        SessionSnapshot session = store.login("a1", "r1", true, false);
        assertTrue(store.updateProfile(session, "user-1", "user-1@example.com", "Name"));
        assertTrue(store.updateTokens(session, "a2", "r2"));
        assertTrue(store.completeSetup(session));

        // Same process: still logged in, and no later write turned the flag back on.
        assertTrue(store.isCurrent(session));
        assertFalse(store.current().rememberMe);
        assertFalse(storage.saved.rememberMe);

        // New process: the first read ends that login and persists the logout.
        SessionStore restarted = new SessionStore(storage);
        SessionSnapshot now = restarted.current();
        assertFalse(now.loggedIn);
        assertNull(now.accessToken);
        assertNull(now.refreshToken);
        assertNull(now.userId);
        assertTrue(now.setupCompleted);
        assertTrue(now.generation > session.generation);
        assertFalse(restarted.isCurrent(session));
        assertSame(now, storage.saved);
        // The flag is back to its default, like after a logout.
        assertTrue(now.rememberMe);
        int saves = storage.saves;
        assertSame(now, restarted.current());
        assertEquals(saves, storage.saves);
    }

    @Test
    public void rememberedLoginSurvivesARestartAndLogoutResetsTheFlag() {
        SessionSnapshot remembered = store.login("a1", "r1", true, true);
        assertTrue(new SessionStore(storage).isCurrent(remembered));

        SessionSnapshot session = store.login("a3", "r3", true, false);
        assertTrue(store.clear(session));
        assertTrue(store.current().rememberMe);
        // The next login decides again.
        assertTrue(new SessionStore(storage).isCurrent(store.login("a4", "r4", true, true)));
    }

    @Test
    public void failedSaveWhileEndingAnUnrememberedLoginKeepsNothingInMemory() {
        store.login("a1", "r1", true, false);
        SessionSnapshot persisted = storage.saved;
        boolean[] broken = {true};
        SessionStore restarted = new SessionStore(new SessionStorage() {
            @Override public SessionSnapshot load() { return storage.load(); }
            @Override public void save(SessionSnapshot state) {
                if (broken[0]) throw new IllegalStateException("disk");
                storage.save(state);
            }
        });

        org.junit.Assert.assertThrows(IllegalStateException.class, restarted::current);
        assertSame(persisted, storage.saved);

        // The logged-in session was never handed out; the next read ends it.
        broken[0] = false;
        assertFalse(restarted.current().loggedIn);
        assertFalse(storage.saved.loggedIn);
    }
}
