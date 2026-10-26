package com.example.fintrack.app.core.network;

import android.content.Context;
import android.text.TextUtils;

import com.example.fintrack.BuildConfig;
import com.example.fintrack.app.core.session.SessionManager;

import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    private static Retrofit retrofit = null;

    public static synchronized Retrofit getClient(Context context) {
        if (retrofit == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            if (BuildConfig.DEBUG) {
                logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
            } else {
                logging.setLevel(HttpLoggingInterceptor.Level.NONE);
            }
            logging.redactHeader("Authorization");

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

            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .addInterceptor(authInterceptor)
                    .addInterceptor(logging)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BuildConfig.BASE_URL)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    public static <T> T createService(Context context, Class<T> serviceClass) {
        return getClient(context).create(serviceClass);
    }
}
