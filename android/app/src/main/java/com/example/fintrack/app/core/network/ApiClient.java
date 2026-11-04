package com.example.fintrack.app.core.network;

import android.content.Context;
import android.text.TextUtils;

import com.example.fintrack.BuildConfig;
import com.example.fintrack.app.core.session.SessionManager;

import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Cấu hình Retrofit và OkHttpClient dùng chung cho toàn bộ ứng dụng để gọi API.
 */
public class ApiClient {

    private static Retrofit retrofit = null;

    /**
     * Khởi tạo và trả về Retrofit Client (Áp dụng Singleton Pattern để chỉ tạo 1 instance duy nhất).
     */
    public static synchronized Retrofit getClient(Context context) {
        if (retrofit == null) {

            // 1. Cấu hình Log để xem Request/Response trên Logcat (Chỉ bật khi ở chế độ DEBUG)
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            if (BuildConfig.DEBUG) {
                logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
            } else {
                logging.setLevel(HttpLoggingInterceptor.Level.NONE);
            }
            logging.redactHeader("Authorization"); // Ẩn token trong log để bảo mật

            // 2. Interceptor tự động gắn Token và Header vào mọi Request đi kèm
            Interceptor authInterceptor = chain -> {
                Request original = chain.request();
                Request.Builder requestBuilder = original.newBuilder();

                // Lấy thông tin phiên đăng nhập hiện tại
                SessionManager sessionManager = SessionManager.getInstance(context.getApplicationContext());
                String token = sessionManager.getAccessToken();
                String requestUserId = sessionManager.getUserId();

                // Nếu có Token, tự động thêm vào Header "Authorization: Bearer <token>"
                if (!TextUtils.isEmpty(token)) {
                    requestBuilder.header("Authorization", "Bearer " + token);
                }

                requestBuilder.header("Accept", "application/json");
                Response response = chain.proceed(requestBuilder.build());

                // Nếu API trả về lỗi 401 (Unauthorized) -> Xóa phiên đăng nhập để bắt đăng nhập lại
                if (response.code() == 401 && java.util.Objects.equals(token, sessionManager.getAccessToken())
                        && java.util.Objects.equals(requestUserId, sessionManager.getUserId())) {
                    sessionManager.clearSession();
                }

                return response;
            };

            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();

            // Nếu đang bật chế độ dùng Mock (dữ liệu giả lập), gắn MockInterceptor vào
            if (BuildConfig.USE_MOCK) {
                clientBuilder.addInterceptor(new MockInterceptor());
            }

            // 3. Cấu hình OkHttpClient (Thêm Interceptor, Timeout, Authenticator làm mới token)
            clientBuilder.addInterceptor(authInterceptor)
                    .addInterceptor(logging)
                    .authenticator(new TokenAuthenticator(context))
                    .connectTimeout(30, TimeUnit.SECONDS) // Thời gian tối đa kết nối mạng
                    .readTimeout(30, TimeUnit.SECONDS)    // Thời gian tối đa chờ đọc dữ liệu trả về
                    .writeTimeout(30, TimeUnit.SECONDS);   // Thời gian tối đa gửi dữ liệu đi

            OkHttpClient okHttpClient = clientBuilder.build();

            // 4. Khởi tạo Retrofit với Base URL và bộ chuyển đổi Gson
            retrofit = new Retrofit.Builder()
                    .baseUrl(BuildConfig.BASE_URL)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    /**
     * Hàm tiện ích giúp tạo ra các Interface gọi API nhanh chóng (Ví dụ: AuthApiService).
     */
    public static <T> T createService(Context context, Class<T> serviceClass) {
        return getClient(context).create(serviceClass);
    }
}