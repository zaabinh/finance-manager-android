package com.fintrack.api;

import com.fintrack.api.dto.response.auth.LoginResponse;
import com.fintrack.api.dto.response.auth.TokenResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthDtoTest {

    private static final TokenResponse TOKEN = new TokenResponse("access", "refresh", "Bearer", 900);

    @Test
    void containsOnboardingStateAndTokensWithoutProfileData() {
        var response = new LoginResponse(true, TOKEN);

        assertThat(response.onboardingCompleted()).isTrue();
        assertThat(response.token()).isSameAs(TOKEN);
    }
}
