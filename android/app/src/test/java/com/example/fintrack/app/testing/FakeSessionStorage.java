package com.example.fintrack.app.testing;

import com.example.fintrack.app.core.session.SessionSnapshot;
import com.example.fintrack.app.core.session.SessionStorage;

/** In-memory "disk". Hand the same instance to a second SessionStore to simulate a process restart. */
public final class FakeSessionStorage implements SessionStorage {
    public SessionSnapshot saved;
    public int saves;

    @Override public SessionSnapshot load() { return saved; }
    @Override public void save(SessionSnapshot state) { saved = state; saves++; }
}
