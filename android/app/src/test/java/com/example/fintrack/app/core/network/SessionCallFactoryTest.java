package com.example.fintrack.app.core.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import androidx.annotation.NonNull;

import com.example.fintrack.app.core.session.SessionSnapshot;
import com.example.fintrack.app.core.session.SessionStore;
import com.example.fintrack.app.testing.FakeSessionStorage;
import com.example.fintrack.app.user.remote.UserApiService;
import com.example.fintrack.app.user.remote.UserProfileResponse;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Real Retrofit over a recording client: proves which session a request is tagged with, and when. */
public class SessionCallFactoryTest {
    private final SessionStore store = new SessionStore(new FakeSessionStorage());
    private final List<Request> issued = new ArrayList<>();

    /** Records the request and returns a call that never answers. */
    private okhttp3.Call record(Request request) {
        issued.add(request);
        return (okhttp3.Call) Proxy.newProxyInstance(okhttp3.Call.class.getClassLoader(), new Class<?>[]{okhttp3.Call.class},
                (proxy, method, args) -> null);
    }

    private UserApiService api(SessionCallFactory calls) {
        return new Retrofit.Builder().baseUrl("http://localhost/").callFactory(calls)
                .addConverterFactory(GsonConverterFactory.create()).build().create(UserApiService.class);
    }

    private static final Callback<ApiResponse<UserProfileResponse>> IGNORE = new Callback<>() {
        @Override public void onResponse(@NonNull Call<ApiResponse<UserProfileResponse>> call, @NonNull Response<ApiResponse<UserProfileResponse>> response) { }
        @Override public void onFailure(@NonNull Call<ApiResponse<UserProfileResponse>> call, @NonNull Throwable error) { }
    };

    @Test
    public void requestIsTaggedWithTheSessionThatIsCurrentWhenTheCallerEnqueuesIt() {
        UserApiService api = api(new SessionCallFactory(this::record, store::current));
        Call<ApiResponse<UserProfileResponse>> preparedWhileLoggedOut = api.getProfile();
        SessionSnapshot first = store.login("a1", "r1", true);

        preparedWhileLoggedOut.enqueue(IGNORE);

        assertEquals(1, issued.size());
        assertSame(first, issued.get(0).tag(SessionSnapshot.class));

        store.clear(first);
        api.getProfile().enqueue(IGNORE);
        SessionSnapshot second = store.login("a2", "r2", true);
        api.getProfile().enqueue(IGNORE);

        assertSame(first, issued.get(0).tag(SessionSnapshot.class)); // unchanged by later logins
        assertFalse(issued.get(1).tag(SessionSnapshot.class).loggedIn);
        assertSame(second, issued.get(2).tag(SessionSnapshot.class));
    }

    @Test
    public void boundFactoryTagsEveryRequestWithItsOwnSessionWhateverIsCurrent() {
        SessionSnapshot origin = store.login("a1", "r1", true);
        UserApiService api = api(new SessionCallFactory(this::record, () -> origin));
        store.clear(origin);
        store.login("a2", "r2", true);

        api.getProfile().enqueue(IGNORE);

        assertSame(origin, issued.get(0).tag(SessionSnapshot.class));
    }
}
