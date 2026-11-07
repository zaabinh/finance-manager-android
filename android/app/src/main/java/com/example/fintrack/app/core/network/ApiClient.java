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
 * Tách biệt client gọi API thông thường và client gọi refresh token để tránh vòng lặp vô hạn.
 */
public class ApiClient {

    private static Retrofit retrofit = null;
    private static Retrofit refreshRetrofit = null;

    /**
     * Khởi tạo và trả về Retrofit Client chuẩn (Có tích hợp AuthInterceptor và TokenAuthenticator).
     */
    public static synchronized Retrofit getClient(Context context) {
        if (retrofit == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            if (BuildConfig.DEBUG) {
                logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
            } else {
                logging.setLevel(HttpLoggingInterceptor.Level.NONE);
            }
            logging.redactHeader("Authorization");

            // Interceptor gắn Access Token vào Header của mọi request thông thường
            Interceptor authInterceptor = chain -> {
                Request original = chain.request();
                Request.Builder requestBuilder = original.newBuilder();

                SessionManager sessionManager = SessionManager.getInstance(context.getApplicationContext());
                String token = sessionManager.getAccessToken();

                if (!TextUtils.isEmpty(token)) {
                    requestBuilder.header("Authorization", "Bearer " + token);
                }

                requestBuilder.header("Accept", "application/json");
                return chain.proceed(requestBuilder.build());
            };

            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();

            if (BuildConfig.USE_MOCK) {
                clientBuilder.addInterceptor(new MockInterceptor());
            }

            clientBuilder.addInterceptor(authInterceptor)
                    .addInterceptor(logging)
                    .authenticator(new TokenAuthenticator(context))
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS);

            OkHttpClient okHttpClient = clientBuilder.build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BuildConfig.BASE_URL)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    /**
     * Khởi tạo và trả về Retrofit Client RIÊNG BIỆT cho việc gọi API Refresh Token.
     * KHÔNG gắn TokenAuthenticator và KHÔNG gắn AuthInterceptor để tuyệt đối tránh vòng lặp vô hạn (Infinite Loop).
     */
    public static synchronized Retrofit getRefreshClient(Context context) {
        if (refreshRetrofit == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            if (BuildConfig.DEBUG) {
                logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
            } else {
                logging.setLevel(HttpLoggingInterceptor.Level.NONE);
            }

            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .addInterceptor(chain -> {
                        Request original = chain.request();
                        Request.Builder requestBuilder = original.newBuilder()
                                .header("Accept", "application/json");
                        return chain.proceed(requestBuilder.build());
                    })
                    .addInterceptor(logging)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();

            refreshRetrofit = new Retrofit.Builder()
                    .baseUrl(BuildConfig.BASE_URL)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return refreshRetrofit;
    }

    /**
     * Hàm tiện ích giúp tạo ra các Interface gọi API nhanh chóng.
     */
    public static <T> T createService(Context context, Class<T> serviceClass) {
        return getClient(context).create(serviceClass);
    }
}
