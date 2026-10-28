package com.example.fintrack.app.core.session;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Lớp quản lý phiên làm việc của người dùng (SessionManager) áp dụng mô hình Singleton.
 * Sử dụng thư viện EncryptedSharedPreferences để mã hóa và lưu trữ an toàn các thông tin nhạy cảm
 * (như Token đăng nhập, ID, Email, Trạng thái đăng nhập...) trực tiếp trên bộ nhớ thiết bị.
 */
public class SessionManager {

    private static final String TAG = "SessionManager";
    private static final String PREF_NAME = "fintrack_session_pref"; // Tên file lưu trữ SharedPreferences

    // Các khóa (Keys) dùng để lưu và truy xuất dữ liệu
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_IS_SETUP_COMPLETED = "is_setup_completed";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_NAME = "user_name";

    private static SessionManager instance;
    private final SharedPreferences pref;

    /**
     * Khởi tạo bộ lưu trữ mã hóa (EncryptedSharedPreferences).
     * Nếu xảy ra lỗi khởi tạo, nó sẽ thử xóa file cũ bị hỏng một lần rồi tạo lại.
     */
    private SessionManager(Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences tempPref;
        try {
            // Tạo khóa chủ (MasterKey) dùng chuẩn mã hóa mạnh AES256_GCM
            MasterKey masterKey = new MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            // Khởi tạo SharedPreferences có mã hóa cả Khóa (Key) và Giá trị (Value)
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
                // Xử lý sự cố: Xóa file cấu hình cũ bị lỗi và thử tạo lại từ đầu
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

    /** Đảm bảo chỉ có duy nhất 1 thể hiện (Instance) của SessionManager được tạo ra trong toàn ứng dụng */
    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager(context);
        }
        return instance;
    }

    /** Lưu Token xác thực và chuyển trạng thái thành Đã đăng nhập */
    public void saveAuthToken(String accessToken, String refreshToken) {
        pref.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .apply();
    }

    /** Lưu thông tin chi tiết hồ sơ người dùng */
    public void saveUserProfile(String userId, String email, String name) {
        pref.edit()
                .putString(KEY_USER_ID, userId)
                .putString(KEY_USER_EMAIL, email)
                .putString(KEY_USER_NAME, name)
                .apply();
    }

    /** Đánh dấu trạng thái đã hoàn tất cài đặt ban đầu hay chưa */
    public void setSetupCompleted(boolean completed) {
        pref.edit()
                .putBoolean(KEY_IS_SETUP_COMPLETED, completed)
                .apply();
    }

    // --- Các hàm Getter để kiểm tra trạng thái và lấy thông tin đã lưu ---

    /** Kiểm tra xem người dùng đã đăng nhập hay chưa */
    public boolean isLoggedIn() {
        return pref.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    /** Kiểm tra xem đã hoàn thành thiết lập ban đầu chưa */
    public boolean isSetupCompleted() {
        return pref.getBoolean(KEY_IS_SETUP_COMPLETED, false);
    }

    /** Lấy AccessToken để gắn vào header khi gọi API */
    public String getAccessToken() {
        return pref.getString(KEY_ACCESS_TOKEN, null);
    }

    /** Lấy RefreshToken dùng để làm mới token */
    public String getRefreshToken() {
        return pref.getString(KEY_REFRESH_TOKEN, null);
    }

    /** Lấy ID của người dùng */
    public String getUserId() {
        return pref.getString(KEY_USER_ID, null);
    }

    /** Lấy Email người dùng */
    public String getUserEmail() {
        return pref.getString(KEY_USER_EMAIL, null);
    }

    /** Lấy Tên người dùng */
    public String getUserName() {
        return pref.getString(KEY_USER_NAME, null);
    }

    /** Xóa toàn bộ dữ liệu phiên làm việc (Dùng khi người dùng bấm Đăng xuất / Logout) */
    public void clearSession() {
        pref.edit().clear().apply();
    }
}