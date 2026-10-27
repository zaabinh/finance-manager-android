package com.example.fintrack.app.core.session;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public class SessionManager {

    private static final String TAG = "SessionManager";
    private static final String PREF_NAME = "fintrack_session_pref";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_IS_SETUP_COMPLETED = "is_setup_completed";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_NAME = "user_name";

    private static SessionManager instance;
    private final SharedPreferences pref;

    private SessionManager(Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences tempPref;
        try {
            MasterKey masterKey = new MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            tempPref = EncryptedSharedPreferences.create(
                    appContext,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences: " + e.getClass().getSimpleName());
            try {
                // Attempt to clear corrupted prefs file once and retry
                appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply();
                MasterKey masterKey = new MasterKey.Builder(appContext)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();

                tempPref = EncryptedSharedPreferences.create(
                        appContext,
                        PREF_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
            } catch (Exception ex) {
                Log.e(TAG, "Fatal secure storage failure", ex);
                throw new RuntimeException("Failed to initialize encrypted session storage. Plaintext fallback is prohibited for security.", ex);
            }
        }
        pref = tempPref;
    }

    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager(context);
        }
        return instance;
    }

    public void saveAuthToken(String accessToken, String refreshToken) {
        pref.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .apply();
    }

    public void saveUserProfile(String userId, String email, String name) {
        pref.edit()
                .putString(KEY_USER_ID, userId)
                .putString(KEY_USER_EMAIL, email)
                .putString(KEY_USER_NAME, name)
                .apply();
    }

    public void setSetupCompleted(boolean completed) {
        pref.edit()
                .putBoolean(KEY_IS_SETUP_COMPLETED, completed)
                .apply();
    }

    public boolean isLoggedIn() {
        return pref.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public boolean isSetupCompleted() {
        return pref.getBoolean(KEY_IS_SETUP_COMPLETED, false);
    }

    public String getAccessToken() {
        return pref.getString(KEY_ACCESS_TOKEN, null);
    }

    public String getRefreshToken() {
        return pref.getString(KEY_REFRESH_TOKEN, null);
    }

    public String getUserId() {
        return pref.getString(KEY_USER_ID, null);
    }

    public String getUserEmail() {
        return pref.getString(KEY_USER_EMAIL, null);
    }

    public String getUserName() {
        return pref.getString(KEY_USER_NAME, null);
    }

    public void clearSession() {
        pref.edit().clear().apply();
    }
}
