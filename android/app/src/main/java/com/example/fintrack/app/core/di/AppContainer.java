package com.example.fintrack.app.core.di;

import android.content.Context;

import com.example.fintrack.BuildConfig;
import com.example.fintrack.app.auth.remote.AuthApiService;
import com.example.fintrack.app.auth.repository.AuthRepository;
import com.example.fintrack.app.budget.remote.BudgetApiService;
import com.example.fintrack.app.category.remote.CategoryApiService;
import com.example.fintrack.app.core.network.MockInterceptor;
import com.example.fintrack.app.core.network.SessionAuthInterceptor;
import com.example.fintrack.app.core.network.SessionCallFactory;
import com.example.fintrack.app.core.network.TokenAuthenticator;
import com.example.fintrack.app.core.session.EncryptedSessionStorage;
import com.example.fintrack.app.core.session.SessionSnapshot;
import com.example.fintrack.app.core.session.SessionStore;
import com.example.fintrack.app.finance.data.FinanceRepository;
import com.example.fintrack.app.finance.data.MockFinanceStore;
import com.example.fintrack.app.finance.remote.FinanceApiBundle;
import com.example.fintrack.app.transaction.remote.TransactionApiService;
import com.example.fintrack.app.user.remote.UserApiService;
import com.example.fintrack.app.user.repository.UserRepository;
import com.example.fintrack.app.wallet.remote.WalletApiService;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Manual dependency container, one per process (owned by FintrackApplication).
 * The only place that constructs the session store, HTTP clients, API services, repositories and the ViewModel factory.
 * Everything is created lazily under the container lock.
 */
public class AppContainer {
    private final Context appContext;
    private final AppExecutors executors;
    private final AppViewModelFactory viewModelFactory = new AppViewModelFactory(this);

    private SessionStore sessionStore;
    private OkHttpClient okHttpClient;
    private Retrofit retrofit;
    private AuthApiService authApi;
    private UserApiService userApi;
    private AuthRepository authRepository;
    private UserRepository userRepository;
    private SessionSnapshot financeOrigin;
    private FinanceRepository financeRepository;

    public AppContainer(Context appContext) {
        this(appContext, AppExecutors.create());
    }

    /** For tests that need their own executors. */
    public AppContainer(Context appContext, AppExecutors executors) {
        this.appContext = appContext;
        this.executors = executors;
    }

    public Context appContext() { return appContext; }
    public AppExecutors executors() { return executors; }
    public AppViewModelFactory viewModelFactory() { return viewModelFactory; }

    /** Encrypted session storage is opened on the first read or write, not at process start. */
    public synchronized SessionStore sessionStore() {
        if (sessionStore == null) sessionStore = new SessionStore(new EncryptedSessionStorage(appContext));
        return sessionStore;
    }

    public synchronized AuthRepository authRepository() {
        if (authRepository == null) authRepository = new AuthRepository(authApi(), executors.main());
        return authRepository;
    }

    public synchronized UserRepository userRepository() {
        if (userRepository == null) userRepository = new UserRepository(userApi(), executors.main());
        return userRepository;
    }

    /**
     * Finance data of the session {@code origin}. Real flavors issue every request as {@code origin}, so it fails
     * before reaching the server once that session has ended; the mock flavor reads that user's file. May throw
     * for a missing user or unavailable storage, so call it from the background task that uses the repository.
     */
    public synchronized FinanceRepository financeRepository(SessionSnapshot origin) {
        if (financeRepository == null || financeOrigin.generation != origin.generation
                || !Objects.equals(financeOrigin.userId, origin.userId)) {
            financeRepository = BuildConfig.USE_MOCK
                    ? new FinanceRepository(new MockFinanceStore(appContext, origin.userId))
                    : remoteFinanceRepository(origin);
            financeOrigin = origin;
        }
        return financeRepository;
    }

    private FinanceRepository remoteFinanceRepository(SessionSnapshot origin) {
        // Finance requests run later on a background thread, so they are tagged with the session of the
        // ViewModel that issued them instead of the session that is current when they execute.
        Retrofit configured = retrofit().newBuilder().callFactory(new SessionCallFactory(this::newCall, () -> origin)).build();
        FinanceApiBundle api = new FinanceApiBundle(configured.create(WalletApiService.class),
                configured.create(CategoryApiService.class), configured.create(BudgetApiService.class),
                configured.create(TransactionApiService.class));
        return new FinanceRepository(api, configured.create(UserApiService.class));
    }

    private synchronized AuthApiService authApi() {
        if (authApi == null) authApi = retrofit().create(AuthApiService.class);
        return authApi;
    }

    private synchronized UserApiService userApi() {
        if (userApi == null) userApi = retrofit().create(UserApiService.class);
        return userApi;
    }

    private synchronized Retrofit retrofit() {
        // The HTTP client (and with it the session store) is only built when the first request is made.
        // Each request is issued as the session that is current when the caller enqueues/executes it.
        if (retrofit == null) retrofit = newRetrofit(new SessionCallFactory(this::newCall, () -> sessionStore().current()));
        return retrofit;
    }

    private okhttp3.Call newCall(Request request) {
        return okHttpClient().newCall(request);
    }

    private static Retrofit newRetrofit(okhttp3.Call.Factory calls) {
        return new Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL)
                .callFactory(calls)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    /** Standard client: authorizes each request as its own session and refreshes the token on 401 through TokenAuthenticator. */
    private synchronized OkHttpClient okHttpClient() {
        if (okHttpClient == null) {
            SessionStore session = sessionStore();
            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            if (BuildConfig.USE_MOCK) builder.addInterceptor(new MockInterceptor());
            okHttpClient = builder
                    .addInterceptor(new SessionAuthInterceptor(session))
                    .addInterceptor(logging())
                    .authenticator(new TokenAuthenticator(session, newRetrofit(refreshClient()).create(AuthApiService.class)))
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();
        }
        return okHttpClient;
    }

    /** Refresh-token client: no auth interceptor and no authenticator, so a failed refresh can never loop. */
    private static OkHttpClient refreshClient() {
        return new OkHttpClient.Builder()
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder().header("Accept", "application/json").build()))
                .addInterceptor(logging())
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    private static HttpLoggingInterceptor logging() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(BuildConfig.DEBUG ? HttpLoggingInterceptor.Level.BASIC : HttpLoggingInterceptor.Level.NONE);
        logging.redactHeader("Authorization");
        return logging;
    }
}
