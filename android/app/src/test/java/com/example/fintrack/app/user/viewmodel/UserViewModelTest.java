package com.example.fintrack.app.user.viewmodel;

import com.example.fintrack.app.core.network.HttpErrorParser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.fintrack.app.core.network.ApiResponse;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.SessionSnapshot;
import com.example.fintrack.app.core.session.SessionStore;
import com.example.fintrack.app.core.util.Event;
import com.example.fintrack.app.testing.FakeApi;
import com.example.fintrack.app.testing.FakeSessionStorage;
import com.example.fintrack.app.testing.InstantLiveData;
import com.example.fintrack.app.testing.StubCall;
import com.example.fintrack.app.user.remote.DeactivateUserRequest;
import com.example.fintrack.app.user.remote.FirstSetupResponse;
import com.example.fintrack.app.user.remote.UserApiService;
import com.example.fintrack.app.user.remote.UserProfileResponse;
import com.example.fintrack.app.user.remote.UserSettingsResponse;
import com.example.fintrack.app.user.repository.UserRepository;
import com.google.gson.Gson;

import org.junit.Rule;
import org.junit.Test;

public class UserViewModelTest {
    @Rule public final InstantLiveData instantLiveData = new InstantLiveData();

    private final FakeSessionStorage storage = new FakeSessionStorage();
    private final SessionStore store = new SessionStore(storage);
    private final FakeApi api = new FakeApi();
    private final UserViewModel model = new UserViewModel(new UserRepository(api.create(UserApiService.class), Runnable::run), store);

    private static final String PROFILE_JSON = "{\"id\":\"user-1\",\"email\":\"new@example.com\",\"displayName\":\"New name\"}";

    private static <T> ApiResponse<T> ok(String json, Class<T> type) {
        return new ApiResponse<>(true, null, new Gson().fromJson(json, type), 200);
    }

    private StubCall<ApiResponse<UserProfileResponse>> profileCall(String endpoint) { return api.call(endpoint, 0); }

    private SessionSnapshot loggedIn(String access, String userId) {
        SessionSnapshot session = store.login(access, "refresh-" + access, false, true);
        store.updateProfile(session, userId, "old@example.com", "Old name");
        return session;
    }

    /** The user logs out and the same account logs in again: a different session. */
    private SessionSnapshot reLogin(SessionSnapshot old) {
        store.clear(old);
        loggedIn("a2", "user-1");
        return store.current();
    }

    // --- profile refresh ---

    @Test
    public void fetchProfile_deliversTheProfileAndStoresItInTheSessionThatAskedForIt() {
        SessionSnapshot session = loggedIn("a1", "user-1");
        ApiResponse<UserProfileResponse> body = ok(PROFILE_JSON, UserProfileResponse.class);

        model.fetchProfile();
        assertEquals(Resource.Status.LOADING, model.getProfileResult().getValue().getStatus());
        profileCall("getProfile").succeed(body);

        Resource<UserProfileResponse> result = model.getProfileResult().getValue();
        assertEquals(Resource.Status.SUCCESS, result.getStatus());
        assertSame(body.getData(), result.getData());
        assertEquals(session.generation, store.current().generation);
        assertEquals("new@example.com", store.current().email);
        assertEquals("New name", store.current().name);
        assertEquals("a1", store.current().accessToken);
    }

    @Test
    public void fetchProfile_reportsTypedEnvelopeFailure() {
        loggedIn("a1", "user-1");
        SessionSnapshot before = store.current();

        model.fetchProfile();
        profileCall("getProfile").succeed(new ApiResponse<>(false, "Tài khoản bị khóa", null, 403));

        Resource<UserProfileResponse> result = model.getProfileResult().getValue();
        assertEquals(Resource.Status.ERROR, result.getStatus());
        assertEquals("LOCAL_API_FAILURE", result.getError().code);
        assertSame(before, store.current());
    }

    @Test
    public void fetchProfile_responseOfAnOlderSessionIsDropped() {
        SessionSnapshot old = loggedIn("a1", "user-1");

        model.fetchProfile();
        SessionSnapshot newer = reLogin(old);
        profileCall("getProfile").succeed(ok(PROFILE_JSON, UserProfileResponse.class));

        assertSame(newer, store.current()); // no write
        assertEquals(Resource.Status.LOADING, model.getProfileResult().getValue().getStatus()); // no UI emission
    }

    @Test
    public void fetchSettings_storesTheProfileOnlyInTheSessionThatAskedForIt() {
        SessionSnapshot old = loggedIn("a1", "user-1");
        String json = "{\"profile\":" + PROFILE_JSON + "}";

        model.fetchSettings();
        api.<ApiResponse<UserSettingsResponse>>call("getSettings", 0).succeed(ok(json, UserSettingsResponse.class));
        assertEquals("New name", store.current().name);
        assertEquals(Resource.Status.SUCCESS, model.getSettingsResult().getValue().getStatus());

        model.fetchSettings();
        SessionSnapshot newer = reLogin(old);
        api.<ApiResponse<UserSettingsResponse>>call("getSettings", 1).succeed(ok(json, UserSettingsResponse.class));
        assertSame(newer, store.current());
        assertEquals(Resource.Status.LOADING, model.getSettingsResult().getValue().getStatus());
    }

    // --- profile update ---

    @Test
    public void updateProfile_storesTheNewNameAndKeepsTheIdentityOfTheSession() {
        loggedIn("a1", "user-1");

        model.updateProfile("New name", "Asia/Tokyo");
        profileCall("updateProfile").succeed(ok("{\"displayName\":\"New name\",\"timeZone\":\"Asia/Tokyo\"}", UserProfileResponse.class));

        assertEquals("New name", store.current().name);
        assertEquals("user-1", store.current().userId);
        assertEquals("old@example.com", store.current().email);
        assertEquals(Resource.Status.SUCCESS, model.getUpdateProfileResult().getValue().getStatus());
    }

    @Test
    public void updateProfile_responseOfAnOlderSessionIsDropped() {
        SessionSnapshot old = loggedIn("a1", "user-1");

        model.updateProfile("New name", "Asia/Tokyo");
        SessionSnapshot newer = reLogin(old);
        profileCall("updateProfile").succeed(ok("{\"displayName\":\"New name\"}", UserProfileResponse.class));

        assertSame(newer, store.current());
        assertEquals(Resource.Status.LOADING, model.getUpdateProfileResult().getValue().getStatus());
    }

    @Test
    public void newerProfileReadSuppressesThePreviousResultInTheSameSession() {
        loggedIn("a1", "user-1");
        model.fetchProfile();
        model.fetchProfile();
        api.<ApiResponse<UserProfileResponse>>call("getProfile", 0)
                .succeed(ok("{\"id\":\"user-1\",\"displayName\":\"Old\"}", UserProfileResponse.class));
        assertEquals(Resource.Status.LOADING, model.getProfileResult().getValue().getStatus());
        api.<ApiResponse<UserProfileResponse>>call("getProfile", 1)
                .succeed(ok("{\"id\":\"user-1\",\"displayName\":\"New\"}", UserProfileResponse.class));
        assertEquals("New", model.getProfileResult().getValue().getData().getDisplayName());
    }

    // --- deactivation ---

    @Test
    public void deactivateAccount_clearsTheSessionOnSuccessAndEmitsOnce() {
        loggedIn("a1", "user-1");

        model.deactivateAccount("secret");
        assertEquals("secret", api.<DeactivateUserRequest>body("deactivateAccount", 0).getCurrentPassword());
        assertTrue(store.current().loggedIn);
        api.<ApiResponse<Void>>call("deactivateAccount", 0).succeed(new ApiResponse<>(true, null, null, 200));

        assertFalse(store.current().loggedIn);
        Event<Resource<Void>> event = model.getDeactivateAccountResult().getValue();
        assertEquals(Resource.Status.SUCCESS, event.getContentIfNotHandled().getStatus());
        assertNull(event.getContentIfNotHandled());
    }

    @Test
    public void deactivateAccount_keepsTheSessionWhenTheServerRefuses() {
        loggedIn("a1", "user-1");
        SessionSnapshot before = store.current();

        model.deactivateAccount("wrong");
        api.<ApiResponse<Void>>call("deactivateAccount", 0).reject(400);

        assertSame(before, store.current());
        assertEquals(Resource.Status.ERROR, model.getDeactivateAccountResult().getValue().getContentIfNotHandled().getStatus());
    }

    @Test
    public void deactivateAccount_clearsOnlyTheSessionItWasStartedFor() {
        SessionSnapshot old = loggedIn("a1", "user-1");

        model.deactivateAccount("secret");
        store.clear(old);
        loggedIn("b1", "user-2");
        SessionSnapshot newer = store.current();
        api.<ApiResponse<Void>>call("deactivateAccount", 0).succeed(new ApiResponse<>(true, null, null, 200));

        assertSame(newer, store.current());
        assertNull(model.getDeactivateAccountResult().getValue());
    }

    // --- setup completion ---

    @Test
    public void completeFirstSetup_marksTheSessionThatSentItOnceTheServerConfirms() {
        SessionSnapshot session = loggedIn("a1", "user-1");

        model.completeFirstSetup(null);
        assertFalse(store.current().setupCompleted);
        api.<ApiResponse<FirstSetupResponse>>call("completeFirstSetup", 0)
                .succeed(ok("{\"onboardingCompleted\":true}", FirstSetupResponse.class));

        assertTrue(store.current().setupCompleted);
        assertEquals(session.generation, store.current().generation);
        assertEquals(Resource.Status.SUCCESS, model.getFirstSetupResult().getValue().getStatus());
    }

    @Test
    public void completeFirstSetup_failureOrOlderSessionLeavesTheSetupFlagAlone() {
        SessionSnapshot old = loggedIn("a1", "user-1");

        model.completeFirstSetup(null);
        api.<ApiResponse<FirstSetupResponse>>call("completeFirstSetup", 0).reject(500);
        assertFalse(store.current().setupCompleted);
        assertEquals(Resource.Status.ERROR, model.getFirstSetupResult().getValue().getStatus());

        model.completeFirstSetup(null);
        SessionSnapshot newer = reLogin(old);
        api.<ApiResponse<FirstSetupResponse>>call("completeFirstSetup", 1)
                .succeed(ok("{\"onboardingCompleted\":true}", FirstSetupResponse.class));
        assertSame(newer, store.current());
        assertFalse(store.current().setupCompleted);
        assertEquals(Resource.Status.LOADING, model.getFirstSetupResult().getValue().getStatus());
    }

    @Test
    public void confirmSetupCompleted_marksSetupAndStoresTheDisplayName() {
        SessionSnapshot session = loggedIn("a1", "user-1");

        model.confirmSetupCompleted("Minh Anh");

        SessionSnapshot now = store.current();
        assertTrue(now.setupCompleted);
        assertEquals("Minh Anh", now.name);
        assertEquals("user-1", now.userId);
        assertEquals("old@example.com", now.email);
        assertEquals(session.generation, now.generation);
    }

    // --- Resource ---

    @Test
    public void resourceSuccess_containsData() {
        Resource<UserProfileResponse> resource = Resource.success(null);
        assertNotNull(resource);
        assertEquals(Resource.Status.SUCCESS, resource.getStatus());
    }

    @Test
    public void resourceError_containsMessage() {
        Resource<UserProfileResponse> resource = Resource.error(HttpErrorParser.from(new java.io.IOException()), null);
        assertNotNull(resource);
        assertEquals(Resource.Status.ERROR, resource.getStatus());
        assertEquals("LOCAL_NETWORK", resource.getError().code);
    }
}
