package com.example.fintrack.app.auth.remote;

import static org.junit.Assert.*;

import com.example.fintrack.app.auth.repository.AuthRepository;
import com.example.fintrack.app.core.error.AppError;
import com.example.fintrack.app.core.network.Resource;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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

    /** Runs one repository request against a server answering {@code code} with an empty body. */
    private static Resource<Void> answer(int code, BiConsumer<AuthRepository, Consumer<Resource<Void>>> request) throws Exception {
        CompletableFuture<Resource<Void>> result = new CompletableFuture<>();
        request.accept(new AuthRepository(serviceReturning(code), Runnable::run), result::complete);
        return result.get(10, TimeUnit.SECONDS);
    }

    @Test public void emptySuccessBodiesReachTheCallerAsSuccessThroughTheRepository() throws Exception {
        assertEquals(Resource.Status.SUCCESS, answer(201, (auth, result) -> auth.register("demo", "demo@x.vn", "Demo12345!", result)).getStatus());
        assertEquals(Resource.Status.SUCCESS, answer(202, (auth, result) -> auth.resendVerification("demo@x.vn", result)).getStatus());
        assertEquals(Resource.Status.SUCCESS, answer(202, (auth, result) -> auth.resendOtp("demo@x.vn", result)).getStatus());
        assertEquals(Resource.Status.SUCCESS, answer(202, (auth, result) -> auth.forgotPassword("demo@x.vn", result)).getStatus());
        assertEquals(Resource.Status.SUCCESS, answer(204, (auth, result) -> auth.verifyEmail("demo@x.vn", "1234", result)).getStatus());
        assertEquals(Resource.Status.SUCCESS, answer(204, (auth, result) -> auth.resetPassword("demo@x.vn", "1234", "Demo12345!", result)).getStatus());
        assertEquals(Resource.Status.SUCCESS, answer(204, (auth, result) -> auth.logout("r", result)).getStatus());
    }

    @Test public void emptyErrorBodyIsATypedHttpErrorNotANetworkError() throws Exception {
        Resource<Void> rejected = answer(409, (auth, result) -> auth.register("demo", "demo@x.vn", "Demo12345!", result));
        assertEquals(Resource.Status.ERROR, rejected.getStatus());
        assertEquals(AppError.Category.HTTP, rejected.getError().category);
        assertEquals(Integer.valueOf(409), rejected.getError().httpStatus);
    }
}
