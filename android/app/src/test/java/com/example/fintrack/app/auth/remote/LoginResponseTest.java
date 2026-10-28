package com.example.fintrack.app.auth.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.example.fintrack.app.core.network.ApiResponse;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;

public class LoginResponseTest {

    @Test
    public void parsesLoginEnvelopeAndNestedToken() {
        String json = "{"
                + "\"success\":true,"
                + "\"message\":\"Login successful\","
                + "\"data\":{"
                + "\"displayName\":\"an.nguyen\","
                + "\"onboardingCompleted\":false,"
                + "\"token\":{"
                + "\"accessToken\":\"access\","
                + "\"refreshToken\":\"refresh\","
                + "\"tokenType\":\"Bearer\","
                + "\"expiresIn\":900}},"
                + "\"code\":200}";

        TypeToken<ApiResponse<LoginResponse>> type = new TypeToken<>() {};
        ApiResponse<LoginResponse> response = new Gson().fromJson(json, type.getType());

        assertEquals("an.nguyen", response.getData().getDisplayName());
        assertFalse(response.getData().isOnboardingCompleted());
        assertEquals("access", response.getData().getToken().getAccessToken());
        assertEquals("refresh", response.getData().getToken().getRefreshToken());
        assertEquals("Bearer", response.getData().getToken().getTokenType());
        assertEquals(900, response.getData().getToken().getExpiresIn());
    }
}
