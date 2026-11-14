package com.example.fintrack.app.core.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.fintrack.app.auth.remote.AuthApiService;
import com.example.fintrack.app.auth.remote.RefreshTokenRequest;
import com.example.fintrack.app.auth.remote.TokenResponse;
import com.example.fintrack.app.core.session.SessionSnapshot;
import com.example.fintrack.app.core.session.SessionStore;
import com.example.fintrack.app.testing.FakeSessionStorage;
import com.example.fintrack.app.testing.StubCall;
import com.google.gson.Gson;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

/** Calls authenticate() directly with hand-built 401 responses; the refresh endpoint is a scripted fake. */
public class TokenAuthenticatorTest {
    private final SessionStore store = new SessionStore(new FakeSessionStorage());
    /** Refresh tokens the fake refresh endpoint received, in order. */
    private final List<String> refreshes = Collections.synchronizedList(new ArrayList<>());
    private volatile StubCall.Execution<ApiResponse<TokenResponse>> refreshAnswer = () -> {
        throw new AssertionError("No refresh expected");
    };
    private final TokenAuthenticator authenticator = new TokenAuthenticator(store, refreshApi());

    private AuthApiService refreshApi() {
        return (AuthApiService) Proxy.newProxyInstance(AuthApiService.class.getClassLoader(), new Class<?>[]{AuthApiService.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("refreshToken")) throw new AssertionError("Unexpected call: " + method.getName());
                    refreshes.add(((RefreshTokenRequest) args[0]).getRefreshToken());
                    return new StubCall<>(refreshAnswer);
                });
    }

    private static ApiResponse<TokenResponse> tokens(String access, String refresh) {
        TokenResponse data = new Gson().fromJson("{\"accessToken\":\"" + access + "\",\"refreshToken\":\"" + refresh + "\"}", TokenResponse.class);
        return new ApiResponse<>(true, null, data, 200);
    }

    private void refreshSucceedsWith(String access, String refresh) {
        refreshAnswer = () -> retrofit2.Response.success(tokens(access, refresh));
    }

    private void refreshFailsWith(int code) {
        refreshAnswer = () -> StubCall.http(code);
    }

    private static Request request(String path, SessionSnapshot origin, String sentToken) {
        Request.Builder request = new Request.Builder().url("http://localhost" + path).tag(SessionSnapshot.class, origin);
        if (sentToken != null) request.header("Authorization", "Bearer " + sentToken);
        return request.build();
    }

    private static Response unauthorized(Request request, Response prior) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(401).message("Unauthorized")
                .priorResponse(prior).build();
    }

    /** A 401 for a protected request that {@code origin} sent with {@code sentToken}. */
    private static Response unauthorized(SessionSnapshot origin, String sentToken) {
        return unauthorized(request("/api/v1/wallets", origin, sentToken), null);
    }

    private SessionSnapshot login(String access, String refresh, String userId) {
        SessionSnapshot session = store.login(access, refresh, true, true);
        store.updateProfile(session, userId, userId + "@example.com", userId);
        return session;
    }

    // --- old session ---

    @Test
    public void oldSession401AfterLogoutIsNotRetriedAndDoesNotTouchTheSession() throws IOException {
        SessionSnapshot old = login("a1", "r1", "user-1");
        store.clear(old);
        SessionSnapshot loggedOut = store.current();

        assertNull(authenticator.authenticate(null, unauthorized(old, "a1")));

        assertTrue(refreshes.isEmpty());
        assertSame(loggedOut, store.current());
    }

    @Test
    public void oldSession401AfterAccountChangeNeitherRetriesNorClearsNorOverwritesTheNewSession() throws IOException {
        SessionSnapshot old = login("a1", "r1", "user-1");
        store.clear(old);
        login("b1", "br1", "user-2");
        SessionSnapshot current = store.current();
        refreshSucceedsWith("stolen", "stolen");

        assertNull(authenticator.authenticate(null, unauthorized(old, "a1")));

        assertTrue(refreshes.isEmpty());
        assertSame(current, store.current());
    }

    @Test
    public void oldSession401AfterSameAccountReLoginIsNotRetriedWithTheNewToken() throws IOException {
        SessionSnapshot old = login("a1", "r1", "user-1");
        store.clear(old);
        login("a2", "r2", "user-1");
        SessionSnapshot current = store.current();
        refreshSucceedsWith("a3", "r3");

        assertNull(authenticator.authenticate(null, unauthorized(old, "a1")));
        // Also when the retry-loop guard would apply: the newer session is never cleared.
        assertNull(authenticator.authenticate(null, unauthorized(request("/api/v1/wallets", old, "a1"), unauthorized(old, "a1"))));

        assertTrue(refreshes.isEmpty());
        assertSame(current, store.current());
    }

    // --- refresh ---

    @Test
    public void expiredTokenIsRefreshedWithTheRefreshTokenOfThatSessionAndKeepsTheGeneration() throws IOException {
        SessionSnapshot session = login("a1", "r1", "user-1");
        refreshSucceedsWith("a2", "r2");

        Request retry = authenticator.authenticate(null, unauthorized(session, "a1"));

        assertEquals("Bearer a2", retry.header("Authorization"));
        assertSame(session, retry.tag(SessionSnapshot.class));
        assertEquals(Collections.singletonList("r1"), refreshes);
        assertEquals("a2", store.current().accessToken);
        assertEquals("r2", store.current().refreshToken);
        assertEquals(session.generation, store.current().generation);
        assertEquals("user-1", store.current().userId);
    }

    @Test
    public void requestSentWithAnAlreadyReplacedTokenIsRetriedWithTheCurrentTokenWithoutRefreshing() throws IOException {
        SessionSnapshot session = login("a1", "r1", "user-1");
        store.updateTokens(session, "a2", "r2");

        Request retry = authenticator.authenticate(null, unauthorized(session, "a1"));

        assertEquals("Bearer a2", retry.header("Authorization"));
        assertTrue(refreshes.isEmpty());
    }

    @Test(timeout = 20_000)
    public void twoConcurrent401sForTheSameTokenRefreshOnceAndBothRetryWithTheNewToken() throws Exception {
        SessionSnapshot session = login("a1", "r1", "user-1");
        CountDownLatch refreshStarted = new CountDownLatch(1);
        CountDownLatch releaseRefresh = new CountDownLatch(1);
        refreshAnswer = () -> {
            refreshStarted.countDown();
            try {
                releaseRefresh.await();
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
            return retrofit2.Response.success(tokens("a2", "r2"));
        };
        AtomicReference<Request> firstRetry = new AtomicReference<>();
        AtomicReference<Request> secondRetry = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread first = authenticateOnThread(session, firstRetry, failure);
        Thread second = authenticateOnThread(session, secondRetry, failure);

        first.start();
        refreshStarted.await();
        second.start();
        // The second 401 waits for the refresh that is in flight instead of starting its own.
        while (second.getState() != Thread.State.BLOCKED) Thread.yield();
        releaseRefresh.countDown();
        first.join();
        second.join();

        assertNull(failure.get());
        assertEquals(Collections.singletonList("r1"), refreshes);
        assertEquals("Bearer a2", firstRetry.get().header("Authorization"));
        assertEquals("Bearer a2", secondRetry.get().header("Authorization"));
        assertEquals("a2", store.current().accessToken);
        assertEquals(session.generation, store.current().generation);
    }

    private Thread authenticateOnThread(SessionSnapshot session, AtomicReference<Request> retry, AtomicReference<Throwable> failure) {
        return new Thread(() -> {
            try {
                retry.set(authenticator.authenticate(null, unauthorized(session, "a1")));
            } catch (Throwable error) {
                failure.set(error);
            }
        });
    }

    @Test
    public void refreshNetworkFailurePreservesTheSession() throws IOException {
        SessionSnapshot session = login("a1", "r1", "user-1");
        SessionSnapshot before = store.current();
        refreshAnswer = () -> { throw new IOException("offline"); };

        assertNull(authenticator.authenticate(null, unauthorized(session, "a1")));

        assertEquals(1, refreshes.size());
        assertSame(before, store.current());
    }

    @Test
    public void refreshRateLimitAndServerErrorsPreserveTheSession() throws IOException {
        SessionSnapshot session = login("a1", "r1", "user-1");
        SessionSnapshot before = store.current();

        for (int code : new int[]{429, 500, 502, 503}) {
            refreshFailsWith(code);
            assertNull(authenticator.authenticate(null, unauthorized(session, "a1")));
            assertSame("HTTP " + code, before, store.current());
        }
        assertEquals(4, refreshes.size());
    }

    @Test
    public void refreshRejectedWith401Or403ClearsTheSession() throws IOException {
        for (int code : new int[]{401, 403}) {
            SessionSnapshot session = login("a1", "r1", "user-1");
            refreshFailsWith(code);

            assertNull(authenticator.authenticate(null, unauthorized(session, "a1")));

            assertFalse("HTTP " + code, store.current().loggedIn);
            assertNull(store.current().accessToken);
            assertTrue(store.current().setupCompleted);
        }
    }

    @Test
    public void refreshRejectedAfterTheSessionChangedClearsOnlyTheMatchingSession() throws IOException {
        SessionSnapshot old = login("a1", "r1", "user-1");
        AtomicReference<SessionSnapshot> newer = new AtomicReference<>();
        refreshAnswer = () -> {
            // The user logs out and back in (same account) while the old session's refresh is in flight.
            store.clear(old);
            login("a2", "r2", "user-1");
            newer.set(store.current());
            return StubCall.http(401);
        };

        assertNull(authenticator.authenticate(null, unauthorized(old, "a1")));

        assertNotNull(newer.get());
        assertSame(newer.get(), store.current());
    }

    @Test
    public void refreshedTokensOfAnEndedSessionAreDroppedAndTheRequestIsNotRetried() throws IOException {
        SessionSnapshot old = login("a1", "r1", "user-1");
        AtomicReference<SessionSnapshot> newer = new AtomicReference<>();
        refreshAnswer = () -> {
            store.clear(old);
            login("b1", "br1", "user-2");
            newer.set(store.current());
            return retrofit2.Response.success(tokens("a2", "r2"));
        };

        assertNull(authenticator.authenticate(null, unauthorized(old, "a1")));

        assertSame(newer.get(), store.current());
        assertEquals("b1", store.current().accessToken);
    }

    @Test
    public void missingRefreshTokenClearsTheSession() throws IOException {
        SessionSnapshot session = store.login("a1", null, true, true);

        assertNull(authenticator.authenticate(null, unauthorized(session, "a1")));

        assertTrue(refreshes.isEmpty());
        assertFalse(store.current().loggedIn);
    }

    @Test
    public void retryLoopGuardClearsTheSessionOfTheRequest() throws IOException {
        SessionSnapshot session = login("a1", "r1", "user-1");
        Response second401 = unauthorized(request("/api/v1/wallets", session, "a1"), unauthorized(session, "a1"));

        assertNull(authenticator.authenticate(null, second401));

        assertTrue(refreshes.isEmpty());
        assertFalse(store.current().loggedIn);
    }

    // --- requests that never refresh ---

    @Test
    public void publicAuthEndpointsAndRequestsWithoutBearerNeverRefreshOrTouchTheSession() throws IOException {
        SessionSnapshot session = login("a1", "r1", "user-1");
        SessionSnapshot before = store.current();

        assertNull(authenticator.authenticate(null, unauthorized(request("/api/v1/auth/login", session, "a1"), null)));
        assertNull(authenticator.authenticate(null, unauthorized(request("/api/v1/auth/refresh", session, "a1"), null)));
        assertNull(authenticator.authenticate(null, unauthorized(session, null)));

        assertTrue(refreshes.isEmpty());
        assertSame(before, store.current());
    }
}
