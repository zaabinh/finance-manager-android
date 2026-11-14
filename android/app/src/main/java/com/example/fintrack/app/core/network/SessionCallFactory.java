package com.example.fintrack.app.core.network;

import androidx.annotation.NonNull;

import com.example.fintrack.app.core.session.SessionSnapshot;

import java.util.function.Supplier;

import okhttp3.Call;
import okhttp3.Request;

/**
 * Tags every request with the session it is issued under; SessionAuthInterceptor and TokenAuthenticator
 * authorize by that tag. Retrofit creates the call on the caller's thread inside enqueue()/execute(), so
 * {@code origin} is read at the moment the caller issues the request, not when it is sent.
 */
public final class SessionCallFactory implements Call.Factory {
    private final Call.Factory delegate;
    private final Supplier<SessionSnapshot> origin;

    public SessionCallFactory(Call.Factory delegate, Supplier<SessionSnapshot> origin) {
        this.delegate = delegate;
        this.origin = origin;
    }

    @NonNull
    @Override
    public Call newCall(@NonNull Request request) {
        return delegate.newCall(request.newBuilder().tag(SessionSnapshot.class, origin.get()).build());
    }
}
