package com.example.fintrack.app.auth.remote;

import static org.junit.Assert.*;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** The backend answers register/resend/forgot with 201/202 and an empty body; that is success, not a network error. */
public class AuthApiServiceTest {
    private static AuthApiService serviceReturning(int code) {
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> new Response.Builder()
                .request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("")
                .body(ResponseBody.create("", MediaType.get("application/json"))).build()).build();
        return new Retrofit.Builder().baseUrl("http://localhost/").client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(AuthApiService.class);
    }

    @Test public void emptySuccessBodiesAreSuccessfulResponses() throws Exception {
        assertEquals(201, serviceReturning(201).register(new RegisterRequest("demo", "demo@x.vn", "Demo12345!")).execute().code());
        assertEquals(202, serviceReturning(202).resendVerification(new ResendOtpRequest("demo@x.vn")).execute().code());
        assertEquals(202, serviceReturning(202).forgotPassword(new ResendOtpRequest("demo@x.vn")).execute().code());
        assertTrue(serviceReturning(204).verifyEmail(new VerifyEmailRequest("demo@x.vn", "1234")).execute().isSuccessful());
        assertTrue(serviceReturning(204).resetPassword(new ResetPasswordRequest("demo@x.vn", "1234", "Demo12345!")).execute().isSuccessful());
        assertTrue(serviceReturning(204).logout(new RefreshTokenRequest("r")).execute().isSuccessful());
    }
}
