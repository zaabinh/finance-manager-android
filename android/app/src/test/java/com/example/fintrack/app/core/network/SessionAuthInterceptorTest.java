package com.example.fintrack.app.core.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.example.fintrack.app.core.session.SessionSnapshot;
import com.example.fintrack.app.core.session.SessionStore;
import com.example.fintrack.app.testing.FakeSessionStorage;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;

import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

public class SessionAuthInterceptorTest {
    private final SessionStore store = new SessionStore(new FakeSessionStorage());
    private final SessionAuthInterceptor interceptor = new SessionAuthInterceptor(store);
    /** The request that left the interceptor; null when it never proceeded. */
    private Request sent;

    private Interceptor.Chain chain(Request request) {
        return (Interceptor.Chain) Proxy.newProxyInstance(Interceptor.Chain.class.getClassLoader(), new Class<?>[]{Interceptor.Chain.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("request")) return request;
                    if (!method.getName().equals("proceed")) throw new AssertionError("Unexpected call: " + method.getName());
                    sent = (Request) args[0];
                    return new Response.Builder().request(sent).protocol(Protocol.HTTP_1_1).code(200).message("OK").build();
                });
    }

    private static Request request(SessionSnapshot origin) {
        Request.Builder request = new Request.Builder().url("http://localhost/api/v1/wallets");
        return origin == null ? request.build() : request.tag(SessionSnapshot.class, origin).build();
    }

    @Test
    public void requestOfAnEndedSessionFailsBeforeItLeaves() {
        SessionSnapshot old = store.login("a1", "r1", true, true);
        store.clear(old);

        assertThrows(IOException.class, () -> interceptor.intercept(chain(request(old))));
        assertNull(sent);

        // Logging back in (same account or not) does not revive it and never lends it the new token.
        store.login("a2", "r2", true, true);

        assertThrows(IOException.class, () -> interceptor.intercept(chain(request(old))));
        assertNull(sent);
    }

    @Test
    public void untaggedAndLoggedOutRequestsAreSentWithoutAuthorization() throws IOException {
        SessionSnapshot loggedOut = store.current();
        store.login("a1", "r1", true, true);

        interceptor.intercept(chain(request(null)));
        assertNull(sent.header("Authorization"));
        assertEquals("application/json", sent.header("Accept"));

        // Issued before login (e.g. the login request itself): stays public even though a session exists now.
        interceptor.intercept(chain(request(loggedOut)));
        assertNull(sent.header("Authorization"));
    }

    @Test
    public void currentRequestCarriesTheRefreshedTokenOfItsSession() throws IOException {
        SessionSnapshot session = store.login("a1", "r1", true, true);

        interceptor.intercept(chain(request(session)));
        assertEquals("Bearer a1", sent.header("Authorization"));

        store.updateTokens(session, "a2", "r2");

        interceptor.intercept(chain(request(session)));
        assertEquals("Bearer a2", sent.header("Authorization"));
        assertEquals("application/json", sent.header("Accept"));
    }
}
