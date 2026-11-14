package com.example.fintrack.app.core.session;

/** Persistence of the session. Only {@link SessionStore} calls it, always under the store's lock. */
public interface SessionStorage {
    /** The persisted state, or null when nothing was ever saved. */
    SessionSnapshot load();

    void save(SessionSnapshot state);
}
