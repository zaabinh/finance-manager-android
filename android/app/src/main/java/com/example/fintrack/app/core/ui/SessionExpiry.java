package com.example.fintrack.app.core.ui;

import android.app.Activity;
import android.content.Intent;

import com.example.fintrack.app.FintrackApplication;
import com.example.fintrack.app.auth.ui.LoginActivity;
import com.example.fintrack.app.core.session.SessionSnapshot;

/** The one way a screen leaves for Login once the session it was opened with has ended. */
public final class SessionExpiry {
    private SessionExpiry() { }

    public static void redirectToLogin(Activity activity) {
        activity.startActivity(new Intent(activity, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        activity.finish();
    }

    /** True if {@code origin} is still the current login. */
    public static boolean isCurrent(Activity activity, SessionSnapshot origin) {
        return FintrackApplication.container(activity).sessionStore().isCurrent(origin);
    }

    /** Redirects to Login and returns true when {@code origin} has ended (logout, expiry or a newer login). */
    public static boolean redirectIfEnded(Activity activity, SessionSnapshot origin) {
        if (isCurrent(activity, origin)) return false;
        redirectToLogin(activity);
        return true;
    }
}
