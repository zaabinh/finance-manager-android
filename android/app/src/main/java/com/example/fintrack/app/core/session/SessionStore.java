package com.example.fintrack.app.core.session;

/**
 * The single owner of session state. Every write except {@link #login} names the session it belongs to and is
 * refused when that session is no longer the current one, so a callback, retry or token refresh of an old login
 * can never overwrite or clear a newer login. Validate-then-write is atomic under the store's lock.
 */
public final class SessionStore {
    private final SessionStorage storage;
    private SessionSnapshot state; // loaded on first use

    public SessionStore(SessionStorage storage) {
        this.storage = storage;
    }

    public synchronized SessionSnapshot current() {
        if (state == null) {
            SessionSnapshot loaded = storage.load();
            state = loaded != null ? loaded : new SessionSnapshot(0, false, null, null, null, null, null, false);
        }
        return state;
    }

    /** True only if {@code session} is a logged-in snapshot of the login that is still the current one. */
    public synchronized boolean isCurrent(SessionSnapshot session) {
        SessionSnapshot now = current();
        return session != null && session.loggedIn && now.loggedIn && session.generation == now.generation;
    }

    /** Starts a new session (a new generation, even for the same account) without profile fields. */
    public synchronized SessionSnapshot login(String accessToken, String refreshToken, boolean setupCompleted) {
        return write(new SessionSnapshot(current().generation + 1, true, accessToken, refreshToken, null, null, null, setupCompleted));
    }

    public synchronized boolean updateTokens(SessionSnapshot expected, String accessToken, String refreshToken) {
        if (!isCurrent(expected)) return false;
        write(new SessionSnapshot(state.generation, true, accessToken, refreshToken, state.userId, state.email, state.name, state.setupCompleted));
        return true;
    }

    public synchronized boolean updateProfile(SessionSnapshot expected, String userId, String email, String name) {
        if (!isCurrent(expected)) return false;
        write(new SessionSnapshot(state.generation, true, state.accessToken, state.refreshToken, userId, email, name, state.setupCompleted));
        return true;
    }

    public synchronized boolean completeSetup(SessionSnapshot expected) {
        if (!isCurrent(expected)) return false;
        write(new SessionSnapshot(state.generation, true, state.accessToken, state.refreshToken, state.userId, state.email, state.name, true));
        return true;
    }

    /** Logs out {@code expected} only. The setup-completed flag is kept; the generation moves on. */
    public synchronized boolean clear(SessionSnapshot expected) {
        if (!isCurrent(expected)) return false;
        write(new SessionSnapshot(state.generation + 1, false, null, null, null, null, null, state.setupCompleted));
        return true;
    }

    private SessionSnapshot write(SessionSnapshot next) {
        storage.save(next); // persisted first: a failed save leaves the in-memory state unchanged
        state = next;
        return next;
    }
}
