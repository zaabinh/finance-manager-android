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
 * Lớp cấu hình chung để khởi tạo Retrofit và OkHttpClient,
 * dùng để gọi API xuyên suốt toàn bộ ứng dụng.
 */
public class ApiClient {

    private static Retrofit retrofit = null;

    /** Khởi tạo và trả về Retrofit Client (áp dụng Singleton pattern để tái sử dụng) */
    public static synchronized Retrofit getClient(Context context) {
        if (retrofit == null) {
            // Cấu hình ghi log (để xem request/response trên Logcat khi ở chế độ DEBUG)
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            if (BuildConfig.DEBUG) {
                logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
            } else {
                logging.setLevel(HttpLoggingInterceptor.Level.NONE);
            }
            logging.redactHeader("Authorization"); // Ẩn token đi khi in log để bảo mật

            // Interceptor tự động gắn Token (nếu có) và Header vào mọi Request gửi đi
            Interceptor authInterceptor = chain -> {
                Request original = chain.request();
                Request.Builder requestBuilder = original.newBuilder();

                // Lấy AccessToken từ SessionManager
                SessionManager sessionManager = SessionManager.getInstance(context.getApplicationContext());
                String token = sessionManager.getAccessToken();
                String requestUserId = sessionManager.getUserId();

                // Nếu đã đăng nhập (có token), tự động thêm vào Header "Authorization: Bearer <token>"
                if (!TextUtils.isEmpty(token)) {
                    requestBuilder.header("Authorization", "Bearer " + token);
                }

                requestBuilder.header("Accept", "application/json");
                Response response = chain.proceed(requestBuilder.build());

                // Nếu gặp lỗi 401 Unauthorized, tự động xóa phiên đăng nhập bảo mật
                if (response.code() == 401 && java.util.Objects.equals(token, sessionManager.getAccessToken())
                        && java.util.Objects.equals(requestUserId, sessionManager.getUserId())) {
                    sessionManager.clearSession();
                }

                return response;
            };

            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();

            // Nếu ứng dụng đang bật chế độ dùng Mock (dữ liệu giả lập), thêm MockInterceptor vào
            if (BuildConfig.USE_MOCK) {
                clientBuilder.addInterceptor(new MockInterceptor());
            }

            // Thêm các Interceptor và thiết lập thời gian chờ (timeout) cho kết nối mạng
            clientBuilder.addInterceptor(authInterceptor)
                    .addInterceptor(logging)
                    .connectTimeout(30, TimeUnit.SECONDS) // Thời gian chờ kết nối tối đa
                    .readTimeout(30, TimeUnit.SECONDS)    // Thời gian chờ đọc dữ liệu tối đa
                    .writeTimeout(30, TimeUnit.SECONDS);   // Thời gian chờ gửi dữ liệu tối đa

            OkHttpClient okHttpClient = clientBuilder.build();

            // Khởi tạo Retrofit với Base URL, OkHttpClient và Gson Converter (chuyển đổi JSON)
            retrofit = new Retrofit.Builder()
                    .baseUrl(BuildConfig.BASE_URL)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    /** Hàm tiện ích giúp tạo ra các Interface gọi API (như AuthApiService) */
    public static <T> T createService(Context context, Class<T> serviceClass) {
        return getClient(context).create(serviceClass);
    }
}
