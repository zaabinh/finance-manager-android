package com.example.fintrack.app.core.session;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Lưu phiên làm việc bằng EncryptedSharedPreferences (Token, ID, Email, trạng thái đăng nhập...).
 * Dùng lại đúng file và các khóa đã dùng trước đây nên người dùng đang đăng nhập vẫn giữ được phiên.
 * File mã hóa chỉ được mở ở lần đọc/ghi đầu tiên. Không bao giờ lưu dạng plaintext.
 */
public final class EncryptedSessionStorage implements SessionStorage {

    private static final String TAG = "EncryptedSessionStorage";
    private static final String PREF_NAME = "fintrack_session_pref"; // Tên file lưu trữ SharedPreferences

    private static final String KEY_GENERATION = "session_generation";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_IS_SETUP_COMPLETED = "is_setup_completed";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_REMEMBER_ME = "remember_me";

    private final Context appContext;
    private SharedPreferences pref;

    public EncryptedSessionStorage(Context appContext) {
        this.appContext = appContext;
    }

    @Override
    public SessionSnapshot load() {
        SharedPreferences pref = pref();
        // A session saved before generations existed has no generation key and starts at 0.
        return new SessionSnapshot(pref.getLong(KEY_GENERATION, 0), pref.getBoolean(KEY_IS_LOGGED_IN, false),
                pref.getString(KEY_ACCESS_TOKEN, null), pref.getString(KEY_REFRESH_TOKEN, null),
                pref.getString(KEY_USER_ID, null), pref.getString(KEY_USER_EMAIL, null), pref.getString(KEY_USER_NAME, null),
                pref.getBoolean(KEY_IS_SETUP_COMPLETED, false),
                // A session saved before "Ghi nhớ tôi" existed has no key and stays remembered.
                pref.getBoolean(KEY_REMEMBER_ME, true));
    }

    @Override
    public void save(SessionSnapshot state) {
        SharedPreferences.Editor editor = pref().edit()
                .putLong(KEY_GENERATION, state.generation)
                .putBoolean(KEY_IS_SETUP_COMPLETED, state.setupCompleted);
        if (state.loggedIn) editor.putBoolean(KEY_IS_LOGGED_IN, true).putBoolean(KEY_REMEMBER_ME, state.rememberMe);
        else editor.remove(KEY_IS_LOGGED_IN).remove(KEY_REMEMBER_ME);
        put(editor, KEY_ACCESS_TOKEN, state.accessToken);
        put(editor, KEY_REFRESH_TOKEN, state.refreshToken);
        put(editor, KEY_USER_ID, state.userId);
        put(editor, KEY_USER_EMAIL, state.email);
        put(editor, KEY_USER_NAME, state.name);
        editor.apply();
    }

    private static void put(SharedPreferences.Editor editor, String key, String value) {
        if (value == null) editor.remove(key);
        else editor.putString(key, value);
    }

    /** Nếu xảy ra lỗi khởi tạo, thử xóa file cũ bị hỏng một lần rồi tạo lại. */
    private synchronized SharedPreferences pref() {
        if (pref == null) {
            try {
                pref = open();
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize EncryptedSharedPreferences: " + e.getClass().getSimpleName());
                try {
                    appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply();
                    pref = open();
                } catch (Exception ex) {
                    Log.e(TAG, "Fatal secure storage failure", ex);
                    throw new RuntimeException("Failed to initialize encrypted session storage. Plaintext fallback is prohibited for security.", ex);
                }
            }
        }
        return pref;
    }

    /** Khóa chủ AES256_GCM; SharedPreferences mã hóa cả Khóa (Key) và Giá trị (Value). */
    private SharedPreferences open() throws Exception {
        MasterKey masterKey = new MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
        return EncryptedSharedPreferences.create(
                appContext,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }
}
