package com.fintrack.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.api.dto.request.auth.LoginRequest;
import com.fintrack.api.dto.request.auth.RegisterRequest;
import com.fintrack.api.exception.AppException;
import com.fintrack.api.repository.UserRepository;
import com.fintrack.api.service.AuthService;
import com.fintrack.api.service.TokenService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthServiceTest {
    @Test
    void registrationNormalizesIdentityAndPersistsOnlyPasswordHash() {
        var users = mock(UserRepository.class);
        var encoder = new BCryptPasswordEncoder(4);
        var service = new AuthService(users, encoder, mock(TokenService.class));

        service.register(new RegisterRequest(" an.nguyen ", " AN@EXAMPLE.COM ", "Strong@123"));

        var hash = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(users).create(eq("an.nguyen"), eq("an@example.com"), hash.capture());
        assertThat(hash.getValue()).isNotEqualTo("Strong@123");
        assertThat(encoder.matches("Strong@123", hash.getValue())).isTrue();
    }

    @Test
    void loginUsesGenericErrorForUnknownOrInvalidPassword() {
        var users = mock(UserRepository.class);
        var service = new AuthService(users, new BCryptPasswordEncoder(4), mock(TokenService.class));
        when(users.findByIdentifier(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("missing@example.com", "Strong@123")))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getStatus().value()).isEqualTo(401);
                    assertThat(exception.getMessage()).isEqualTo("Invalid credentials");
                });
    }
}
