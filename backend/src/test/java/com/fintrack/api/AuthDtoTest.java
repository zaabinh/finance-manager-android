package com.fintrack.api;

import com.fintrack.api.dto.response.auth.LoginResponse;
import com.fintrack.api.dto.response.auth.TokenResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthDtoTest {

    private static final TokenResponse TOKEN = new TokenResponse("access", "refresh", "Bearer", 900);

    @Test
    void usesDisplayNameWhenAvailable() {
        var response = LoginResponse.of("An Nguyen", "an.nguyen", true, TOKEN);

        assertThat(response.displayName()).isEqualTo("An Nguyen");
        assertThat(response.onboardingCompleted()).isTrue();
        assertThat(response.token()).isSameAs(TOKEN);
    }

    @Test
    void fallsBackToUsernameWhenDisplayNameIsMissing() {
        assertThat(LoginResponse.of(null, "an.nguyen", false, TOKEN).displayName()).isEqualTo("an.nguyen");
        assertThat(LoginResponse.of("  ", "an.nguyen", false, TOKEN).displayName()).isEqualTo("an.nguyen");
    }
}
