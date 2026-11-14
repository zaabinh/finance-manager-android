package com.example.fintrack.app.core.network;

import androidx.annotation.NonNull;

import com.example.fintrack.app.core.session.SessionSnapshot;
import com.example.fintrack.app.core.session.SessionStore;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Authorizes a request with the session it was issued under (its {@link SessionSnapshot} tag), never with
 * "whatever token is current". A request of a session that has ended fails before it leaves the device.
 */
public final class SessionAuthInterceptor implements Interceptor {
    private final SessionStore store;

    public SessionAuthInterceptor(SessionStore store) {
        this.store = store;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        Request.Builder request = original.newBuilder().header("Accept", "application/json");
        SessionSnapshot origin = original.tag(SessionSnapshot.class);
        if (origin != null && origin.loggedIn) {
            // One read: generation and token below always belong to the same session.
            SessionSnapshot now = store.current();
            if (!now.loggedIn || now.generation != origin.generation) throw new IOException("Session changed before request");
            // The token may have been refreshed since the request was issued.
            if (now.accessToken != null && !now.accessToken.isEmpty()) request.header("Authorization", "Bearer " + now.accessToken);
        }
        return chain.proceed(request.build());
    }
}
