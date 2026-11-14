package com.example.fintrack.app.auth.ui;

import com.example.fintrack.app.core.util.Event;
import com.example.fintrack.app.core.error.AppError;

import com.example.fintrack.app.core.network.HttpErrorParser;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Instrumentation;
import android.content.Intent;
import android.widget.EditText;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.fintrack.R;
import com.example.fintrack.app.auth.remote.LoginResponse;
import com.example.fintrack.app.auth.viewmodel.AuthViewModel;
import com.example.fintrack.app.core.network.Resource;
import com.example.fintrack.app.core.session.SessionStore;
import com.example.fintrack.app.FintrackApplication;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Response;

@RunWith(AndroidJUnit4.class)
public class LoginVerificationTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private Intent verificationIntent;
    private final Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor() {
        @Override
        public Instrumentation.ActivityResult onStartActivity(Intent intent) {
            verificationIntent = intent;
            return new Instrumentation.ActivityResult(0, null);
        }
    };

    @Before
    public void setUp() {
        logOut();
    }

    private void logOut() {
        SessionStore store = FintrackApplication.container(instrumentation.getTargetContext()).sessionStore();
        store.clear(store.current());
    }

    @After
    public void tearDown() {
        instrumentation.removeMonitor(monitor);
        logOut();
    }

    @Test
    public void unverifiedEmailErrorShowsDialogAndAllowsCancel() {
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            instrumentation.addMonitor(monitor);
            scenario.onActivity(activity -> {
                AppError error = HttpErrorParser.parse(Response.error(403,
                        ResponseBody.create(MediaType.get("application/json"),
                                "{\"errorCode\":\"AUTH_EMAIL_NOT_VERIFIED\"}")));
                assertEquals("AUTH_EMAIL_NOT_VERIFIED", error.code);
                AuthViewModel model = new ViewModelProvider(activity, FintrackApplication.container(activity).viewModelFactory()).get(AuthViewModel.class);
                ((MutableLiveData<Resource<LoginResponse>>) model.getLoginResult())
                        .setValue(Resource.error(error, null));
            });
            onView(withText("Tài khoản chưa xác thực Email")).check(matches(isDisplayed()));
            onView(withText("Để sau")).perform(click());
            assertNull(verificationIntent);
        }
    }

    @Test
    public void resendSuccessOpensVerificationWithEmail() {
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            instrumentation.addMonitor(monitor);
            scenario.onActivity(activity -> {
                ((EditText) activity.findViewById(R.id.etUsernameOrEmail)).setText("demo@fintrack.com");
                AuthViewModel model = new ViewModelProvider(activity, FintrackApplication.container(activity).viewModelFactory()).get(AuthViewModel.class);
                ((MutableLiveData<Event<Resource<Void>>>) model.getResendVerificationResult())
                        .setValue(new Event<>(Resource.success(null)));
            });
            assertNotNull(verificationIntent);
            assertEquals(VerifyEmailActivity.class.getName(), verificationIntent.getComponent().getClassName());
            assertEquals("demo@fintrack.com", verificationIntent.getStringExtra(VerifyEmailActivity.EXTRA_EMAIL));
        }
    }

    @Test
    public void resendErrorStaysOnLogin() {
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            instrumentation.addMonitor(monitor);
            scenario.onActivity(activity -> {
                AuthViewModel model = new ViewModelProvider(activity, FintrackApplication.container(activity).viewModelFactory()).get(AuthViewModel.class);
                ((MutableLiveData<Event<Resource<Void>>>) model.getResendVerificationResult())
                        .setValue(new Event<>(Resource.error(HttpErrorParser.from(new java.io.IOException()), null)));
            });
            assertNull(verificationIntent);
        }
    }
}
