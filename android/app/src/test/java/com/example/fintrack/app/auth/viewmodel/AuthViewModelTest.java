package com.example.fintrack.app.auth.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.fintrack.app.auth.remote.AuthApiService;
import com.example.fintrack.app.auth.remote.LoginRequest;
import com.example.fintrack.app.auth.remote.LoginResponse;
import com.example.fintrack.app.auth.remote.RefreshTokenRequest;
import com.example.fintrack.app.auth.repository.AuthRepository;
import com.example.fintrack.app.core.network.ApiResponse;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.SessionSnapshot;
import com.example.fintrack.app.core.session.SessionStore;
import com.example.fintrack.app.core.util.Event;
import com.example.fintrack.app.testing.FakeApi;
import com.example.fintrack.app.testing.FakeSessionStorage;
import com.example.fintrack.app.testing.InstantLiveData;
import com.example.fintrack.app.testing.StubCall;
import com.example.fintrack.app.user.remote.UserApiService;
import com.example.fintrack.app.user.remote.UserProfileResponse;
import com.example.fintrack.app.user.repository.UserRepository;
import com.google.gson.Gson;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

/** Login/logout coordination: which session each response may write to, and what the screen is told. */
public class AuthViewModelTest {
    @Rule public final InstantLiveData instantLiveData = new InstantLiveData();

    private final FakeSessionStorage storage = new FakeSessionStorage();
    private final SessionStore store = new SessionStore(storage);
    private final FakeApi api = new FakeApi();
    private final AuthViewModel model = newModel();

    private AuthViewModel newModel() {
        return new AuthViewModel(new AuthRepository(api.create(AuthApiService.class), Runnable::run),
                new UserRepository(api.create(UserApiService.class), Runnable::run), store);
    }

    private static ApiResponse<LoginResponse> tokens(String access, String refresh, boolean onboardingCompleted) {
        String json = "{\"onboardingCompleted\":" + onboardingCompleted + ",\"token\":{\"accessToken\":\"" + access
                + "\",\"refreshToken\":\"" + refresh + "\"}}";
        return new ApiResponse<>(true, null, new Gson().fromJson(json, LoginResponse.class), 200);
    }

    private static ApiResponse<UserProfileResponse> profile(String id) {
        String json = "{\"id\":\"" + id + "\",\"email\":\"" + id + "@example.com\",\"displayName\":\"Name " + id + "\"}";
        return new ApiResponse<>(true, null, new Gson().fromJson(json, UserProfileResponse.class), 200);
    }

    private StubCall<ApiResponse<LoginResponse>> loginCall(int index) { return api.call("login", index); }
    private StubCall<ApiResponse<UserProfileResponse>> profileCall(int index) { return api.call("getProfile", index); }
    private StubCall<Void> logoutCall(int index) { return api.call("logout", index); }

    private AuthViewModel.LoginDestination takeNavigation() {
        Event<AuthViewModel.LoginDestination> event = model.getLoginNavigation().getValue();
        return event == null ? null : event.getContentIfNotHandled();
    }

    private SessionSnapshot loggedIn(String access, String refresh, String userId) {
        SessionSnapshot session = store.login(access, refresh, true);
        store.updateProfile(session, userId, userId + "@example.com", "Name " + userId);
        return session;
    }

    // --- login ---

    @Test
    public void login_sendsCredentialsAndReportsARejectedLoginWithoutTouchingTheSession() {
        model.login("demo@fintrack.com", "secret", true);

        LoginRequest sent = api.body("login", 0);
        assertEquals("demo@fintrack.com", sent.getUsernameOrEmail());
        assertEquals("secret", sent.getPassword());
        assertEquals(Resource.Status.LOADING, model.getLoginResult().getValue().getStatus());

        loginCall(0).fail(new IOException("offline"));

        assertEquals(Resource.Status.ERROR, model.getLoginResult().getValue().getStatus());
        assertFalse(store.current().loggedIn);
        assertEquals(0, storage.saves);
        assertEquals(0, api.count("getProfile"));
        assertNull(takeNavigation());
    }

    @Test
    public void loginSuccess_writesTheSessionOnceAndEmitsOneNavigationEvent() {
        model.login("demo", "secret", true);
        ApiResponse<LoginResponse> body = tokens("a1", "r1", true);
        loginCall(0).succeed(body);

        // Tokens are stored as a new session; the profile of that session is being loaded.
        SessionSnapshot session = store.current();
        assertTrue(session.loggedIn);
        assertEquals("a1", session.accessToken);
        assertEquals("r1", session.refreshToken);
        assertTrue(session.setupCompleted);
        assertNull(session.userId);
        assertEquals(1, api.count("getProfile"));
        assertNull(takeNavigation());
        assertSame(body.getData(), model.getLoginResult().getValue().getData());

        profileCall(0).succeed(profile("user-1"));

        assertEquals(session.generation, store.current().generation);
        assertEquals("user-1", store.current().userId);
        assertEquals("user-1@example.com", store.current().email);
        assertEquals("Name user-1", store.current().name);
        assertEquals(2, storage.saves); // login + profile, nothing else
        assertEquals(AuthViewModel.LoginDestination.HOME, takeNavigation());
        // A recreated screen observes the same event again: nothing to navigate to, nothing written.
        assertNull(takeNavigation());
        assertEquals(2, storage.saves);
    }

    @Test
    public void login_sendsRememberMeAndStoresItWithTheSession() {
        model.login("demo", "secret", false);
        assertFalse(new Gson().toJsonTree(api.<LoginRequest>body("login", 0)).getAsJsonObject().get("rememberMe").getAsBoolean());
        loginCall(0).succeed(tokens("a1", "r1", true));
        assertFalse(store.current().rememberMe);
        profileCall(0).succeed(profile("user-1"));
        // Still the same login, and it is persisted as one that ends when the app is closed.
        assertTrue(store.current().loggedIn);
        assertFalse(storage.saved.rememberMe);
        assertEquals(AuthViewModel.LoginDestination.HOME, takeNavigation());

        model.login("demo", "secret", true);
        assertTrue(new Gson().toJsonTree(api.<LoginRequest>body("login", 1)).getAsJsonObject().get("rememberMe").getAsBoolean());
        loginCall(1).succeed(tokens("a2", "r2", true));
        assertTrue(storage.saved.rememberMe);
    }

    @Test
    public void loginSuccess_forAnAccountThatHasNotFinishedOnboardingNavigatesToSetup() {
        model.login("demo", "secret", true);
        loginCall(0).succeed(tokens("a1", "r1", false));
        profileCall(0).succeed(profile("user-1"));

        assertFalse(store.current().setupCompleted);
        assertEquals(AuthViewModel.LoginDestination.SETUP, takeNavigation());
    }

    @Test
    public void loginResponseWithoutAnAccessTokenIsReportedAndCreatesNoSession() {
        model.login("demo", "secret", true);
        loginCall(0).succeed(new ApiResponse<>(true, null, new LoginResponse(), 200));

        assertEquals(Resource.Status.SUCCESS, model.getLoginResult().getValue().getStatus());
        assertFalse(store.current().loggedIn);
        assertEquals(0, api.count("getProfile"));
        assertNull(takeNavigation());
    }

    @Test
    public void obsoleteLoginAttemptIsIgnored() {
        model.login("first", "secret", true);
        model.login("second", "secret", true);

        loginCall(0).succeed(tokens("old", "old-r", true));

        assertFalse(store.current().loggedIn);
        assertEquals(0, storage.saves);
        assertEquals(0, api.count("getProfile"));
        assertEquals(Resource.Status.LOADING, model.getLoginResult().getValue().getStatus());

        loginCall(1).succeed(tokens("new", "new-r", true));
        profileCall(0).succeed(profile("user-2"));

        assertEquals("new", store.current().accessToken);
        assertEquals("user-2", store.current().userId);
        assertEquals(AuthViewModel.LoginDestination.HOME, takeNavigation());
    }

    @Test
    public void profileResponseOfASupersededLoginIsIgnored() {
        model.login("first", "secret", true);
        loginCall(0).succeed(tokens("a1", "r1", true));
        SessionSnapshot first = store.current();

        model.login("second", "secret", true);
        // The half-finished first session is dropped as soon as it is superseded.
        assertFalse(store.current().loggedIn);
        loginCall(1).succeed(tokens("b1", "br1", false));
        SessionSnapshot second = store.current();
        int saves = storage.saves;

        profileCall(0).succeed(profile("user-1"));

        assertSame(second, store.current());
        assertEquals(saves, storage.saves);
        assertNull(takeNavigation());
        assertFalse(store.isCurrent(first));

        profileCall(1).succeed(profile("user-2"));

        assertEquals("user-2", store.current().userId);
        assertEquals("b1", store.current().accessToken);
        assertEquals(AuthViewModel.LoginDestination.SETUP, takeNavigation());
        assertNull(takeNavigation());
    }

    @Test
    public void profileFailureClearsItsOwnSessionAndReportsTheError() {
        model.login("demo", "secret", true);
        loginCall(0).succeed(tokens("a1", "r1", true));

        profileCall(0).reject(500);

        assertFalse(store.current().loggedIn);
        assertNull(store.current().accessToken);
        assertEquals(Resource.Status.ERROR, model.getLoginResult().getValue().getStatus());
        assertNull(takeNavigation());
    }

    @Test
    public void profileFailureNeverClearsANewerSession() {
        model.login("demo", "secret", true);
        loginCall(0).succeed(tokens("a1", "r1", true));
        // Another login (e.g. from a second screen) replaced the session while the profile was loading.
        loggedIn("b1", "br1", "user-2");
        SessionSnapshot newer = store.current();

        profileCall(0).reject(500);

        assertSame(newer, store.current());
        assertNull(takeNavigation());
    }

    @Test
    public void profileSuccessNeverWritesIntoANewerSessionOrNavigates() {
        model.login("demo", "secret", true);
        loginCall(0).succeed(tokens("a1", "r1", true));
        loggedIn("b1", "br1", "user-2");
        SessionSnapshot newer = store.current();

        profileCall(0).succeed(profile("user-1"));

        assertSame(newer, store.current());
        assertNull(takeNavigation());
    }

    @Test
    public void discardIncompleteSession_removesALeftoverHalfSessionButNotALoginInProgress() {
        // Left over from a previous run: tokens saved, profile never loaded.
        store.login("stale", "stale-r", true);
        model.discardIncompleteSession();
        assertFalse(store.current().loggedIn);

        // Login in progress in this (retained) ViewModel while the screen is recreated.
        model.login("demo", "secret", true);
        loginCall(0).succeed(tokens("a1", "r1", true));
        SessionSnapshot inProgress = store.current();
        model.discardIncompleteSession();
        assertSame(inProgress, store.current());

        profileCall(0).succeed(profile("user-1"));
        assertEquals(AuthViewModel.LoginDestination.HOME, takeNavigation());

        // A complete session is never discarded.
        SessionSnapshot complete = store.current();
        model.discardIncompleteSession();
        assertSame(complete, store.current());
    }

    // --- logout ---

    @Test
    public void logout_sendsTheRefreshTokenAndClearsTheSessionOnSuccess() {
        loggedIn("a1", "r1", "user-1");

        model.logout();

        RefreshTokenRequest sent = api.body("logout", 0);
        assertEquals("r1", sent.getRefreshToken());
        assertTrue(store.current().loggedIn); // not before the server answered
        assertNull(model.getLogoutResult().getValue());

        logoutCall(0).succeed(null); // 204 with an empty body

        assertFalse(store.current().loggedIn);
        assertTrue(store.current().setupCompleted);
        Event<Resource<Void>> event = model.getLogoutResult().getValue();
        assertEquals(Resource.Status.SUCCESS, event.getContentIfNotHandled().getStatus());
        assertNull(event.getContentIfNotHandled()); // one-time
    }

    @Test
    public void logout_clearsTheLocalSessionEvenWhenTheServerCallFails() {
        loggedIn("a1", "r1", "user-1");

        model.logout();
        logoutCall(0).fail(new IOException("offline"));

        assertFalse(store.current().loggedIn);
        assertEquals(Resource.Status.ERROR, model.getLogoutResult().getValue().getContentIfNotHandled().getStatus());
    }

    @Test
    public void logout_clearsOnlyTheSessionItWasStartedFor() {
        SessionSnapshot old = loggedIn("a1", "r1", "user-1");

        model.logout();
        // The same account is logged in again before the logout response arrives.
        store.clear(old);
        loggedIn("a2", "r2", "user-1");
        SessionSnapshot newer = store.current();

        logoutCall(0).reject(500);

        assertSame(newer, store.current());
        assertEquals("r1", api.<RefreshTokenRequest>body("logout", 0).getRefreshToken());
        assertNull(model.getLogoutResult().getValue());
    }
}
