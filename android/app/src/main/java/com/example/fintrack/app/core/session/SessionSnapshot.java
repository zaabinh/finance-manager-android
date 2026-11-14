package com.example.fintrack.app.core.session;

/**
 * Immutable state of the session at one moment. {@link #generation} identifies one login: it changes on every
 * login and logout (also for the same account) and stays the same while that login's tokens/profile are updated.
 */
public final class SessionSnapshot {
    public final long generation;
    public final boolean loggedIn;
    public final String accessToken;
    public final String refreshToken;
    public final String userId;
    public final String email;
    public final String name;
    public final boolean setupCompleted;

    public SessionSnapshot(long generation, boolean loggedIn, String accessToken, String refreshToken,
            String userId, String email, String name, boolean setupCompleted) {
        this.generation = generation;
        this.loggedIn = loggedIn;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.email = email;
        this.name = name;
        this.setupCompleted = setupCompleted;
    }
}
