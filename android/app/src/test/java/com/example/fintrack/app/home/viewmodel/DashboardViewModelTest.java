package com.example.fintrack.app.home.viewmodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.example.fintrack.app.core.error.AppError;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.SessionSnapshot;
import com.example.fintrack.app.core.session.SessionStore;
import com.example.fintrack.app.home.remote.DashboardApiService;
import com.example.fintrack.app.home.remote.DashboardResponse;
import com.example.fintrack.app.home.repository.DashboardRepository;
import com.example.fintrack.app.testing.FakeApi;
import com.example.fintrack.app.testing.FakeSessionStorage;
import com.example.fintrack.app.testing.InstantLiveData;
import com.example.fintrack.app.testing.StubCall;
import com.google.gson.Gson;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

/** Which dashboard response may reach the Home screen, and with what typed result. */
public class DashboardViewModelTest {
    @Rule public final InstantLiveData instantLiveData = new InstantLiveData();

    private final SessionStore store = new SessionStore(new FakeSessionStorage());
    private final FakeApi api = new FakeApi();
    private final DashboardViewModel model = new DashboardViewModel(
            new DashboardRepository(api.create(DashboardApiService.class), Runnable::run), store);

    private static DashboardResponse summary(long total) {
        return new Gson().fromJson("{\"assets\":{\"totalMinor\":" + total + ",\"availableMinor\":70,\"savingsMinor\":30},"
                + "\"budget\":{\"count\":2,\"progressPercent\":40,\"limitMinor\":1000,\"spentMinor\":400,"
                + "\"remainingMinor\":600,\"worstStatus\":\"EXCEEDED\"}}", DashboardResponse.class);
    }

    private StubCall<DashboardResponse> call(int index) { return api.call("dashboard", index); }

    private SessionSnapshot loggedIn(String access) {
        SessionSnapshot session = store.login(access, "refresh-" + access, true);
        store.updateProfile(session, "user-1", "user-1@example.com", "Name");
        return session;
    }

    @Test
    public void refresh_deliversTheServersAggregatesUnchanged() {
        loggedIn("a1");
        DashboardResponse body = summary(100);

        model.refresh();
        assertEquals(Resource.Status.LOADING, model.summary().getValue().getStatus());
        call(0).succeed(body);

        Resource<DashboardResponse> result = model.summary().getValue();
        assertEquals(Resource.Status.SUCCESS, result.getStatus());
        assertSame(body, result.getData());
        assertEquals(100, result.getData().assets.totalMinor);
        assertEquals(40, result.getData().budget.progressPercent);
        assertEquals("EXCEEDED", result.getData().budget.worstStatus);
    }

    @Test
    public void responseOfASessionThatHasEndedIsDropped() {
        SessionSnapshot old = loggedIn("a1");
        model.refresh();

        // Logout, then the same account logs in again: a different session.
        store.clear(old);
        loggedIn("a2");
        call(0).succeed(summary(100));
        assertEquals(Resource.Status.LOADING, model.summary().getValue().getStatus());

        model.refresh();
        store.clear(store.current());
        call(1).fail(new IOException("offline"));
        assertEquals(Resource.Status.LOADING, model.summary().getValue().getStatus());
    }

    @Test
    public void onlyTheLatestRefreshIsDelivered() {
        loggedIn("a1");
        model.refresh();
        model.refresh();
        DashboardResponse latest = summary(200);

        call(1).succeed(latest);
        call(0).succeed(summary(100)); // obsolete: arrives late and is ignored

        assertSame(latest, model.summary().getValue().getData());
    }

    @Test
    public void failuresAreTypedErrors() {
        loggedIn("a1");

        model.refresh();
        call(0).fail(new IOException("offline"));
        assertEquals(AppError.Category.NETWORK, model.summary().getValue().getError().category);

        model.refresh();
        call(1).reject(503);
        assertEquals(Integer.valueOf(503), model.summary().getValue().getError().httpStatus);

        // 200 without a body, or without the assets/budget blocks, cannot be shown.
        model.refresh();
        call(2).succeed(null);
        assertEquals("LOCAL_MISSING_DATA", model.summary().getValue().getError().code);

        model.refresh();
        call(3).succeed(new DashboardResponse());
        assertEquals(Resource.Status.ERROR, model.summary().getValue().getStatus());
        assertEquals("LOCAL_MISSING_DATA", model.summary().getValue().getError().code);
        assertNull(model.summary().getValue().getData());
    }

    @Test
    public void clearedModelDeliversNothing() {
        loggedIn("a1");
        model.refresh();

        model.onCleared();
        call(0).succeed(summary(100));

        assertEquals(Resource.Status.LOADING, model.summary().getValue().getStatus());
    }
}
