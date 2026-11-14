package com.example.fintrack.app.core.network;

public interface RequestHandle {
    RequestHandle NONE = () -> { };
    /** Suppresses delivery; canceling a mutation does not imply that the server did not commit it. */
    void cancel();
}
