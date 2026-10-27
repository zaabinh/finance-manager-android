package com.fintrack.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import java.lang.reflect.Constructor;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Authentication DTO and component contracts")
class AuthContractTest {

    private static final String REGISTER_REQUEST = "com.fintrack.api.dto.request.auth.RegisterRequest";
    private static final String LOGIN_REQUEST = "com.fintrack.api.dto.request.auth.LoginRequest";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void registerRequestContainsOnlyUsernameEmailAndPassword() throws Exception {
        assertThat(recordComponents(REGISTER_REQUEST))
                .containsExactly("username", "email", "password")
                .doesNotContain("confirmPassword");
    }

    @Test
    void loginRequestContainsOnlyIdentifierAndPassword() throws Exception {
        assertThat(recordComponents(LOGIN_REQUEST))
                .containsExactly("usernameOrEmail", "password");
    }

    @Test
    void backendEnforcesTheFullPasswordPolicy() throws Exception {
        assertInvalidPassword("alllowercase1!");
        assertInvalidPassword("ALLUPPERCASE1!");
        assertInvalidPassword("NoDigitsHere!");
        assertInvalidPassword("NoSpecial123");
        assertInvalidPassword("Short1!");
    }

    @Test
    void loginResponseMatchesTheAndroidContractAndContainsNoUserProfile() throws Exception {
        Class<?> tokenType = Class.forName("com.fintrack.api.dto.response.auth.TokenResponse");
        Object token = tokenType.getDeclaredConstructor(String.class, String.class, String.class, long.class)
                .newInstance("access-token", "refresh-token", "Bearer", 900L);
        Class<?> responseType = Class.forName("com.fintrack.api.dto.response.auth.LoginResponse");
        Object response = responseType.getDeclaredConstructor(String.class, boolean.class, tokenType)
                .newInstance("An Nguyen", false, token);

        JsonNode json = OBJECT_MAPPER.valueToTree(response);

        assertThat(json.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("displayName", "onboardingCompleted", "token");
        assertThat(json.path("token").fieldNames()).toIterable()
                .containsExactlyInAnyOrder("accessToken", "refreshToken", "tokenType", "expiresIn");
        assertThat(json.has("user")).isFalse();
        assertThat(json.has("email")).isFalse();
        assertThat(json.has("userId")).isFalse();
    }

    @Test
    void loginResponseFallsBackToUsernameForMissingDisplayName() throws Exception {
        Class<?> tokenType = Class.forName("com.fintrack.api.dto.response.auth.TokenResponse");
        Object token = tokenType.getDeclaredConstructor(String.class, String.class, String.class, long.class)
                .newInstance("access-token", "refresh-token", "Bearer", 900L);
        Class<?> responseType = Class.forName("com.fintrack.api.dto.response.auth.LoginResponse");

        Object response = responseType
                .getDeclaredMethod("of", String.class, String.class, boolean.class, tokenType)
                .invoke(null, "  ", "an.nguyen", false, token);

        assertThat(OBJECT_MAPPER.valueToTree(response).path("displayName").asText()).isEqualTo("an.nguyen");
    }

    @Test
    void requiredAuthenticationComponentsExistAtTheirLayerBoundaries() {
        assertThat(loadableClasses(
                "com.fintrack.api.controller.AuthController",
                "com.fintrack.api.service.AuthService",
                "com.fintrack.api.service.TokenService",
                "com.fintrack.api.repository.UserRepository",
                "com.fintrack.api.repository.RefreshSessionRepository"))
                .as("controller, service, token and persistence boundaries")
                .isEmpty();
    }

    private static void assertInvalidPassword(String password) throws Exception {
        Class<?> requestType = Class.forName(REGISTER_REQUEST);
        Constructor<?> constructor = requestType.getDeclaredConstructor(String.class, String.class, String.class);
        Object request = constructor.newInstance("an.nguyen", "an@example.com", password);
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Set<String> invalidFields = validatorFactory.getValidator().validate(request).stream()
                    .map(violation -> violation.getPropertyPath().toString())
                    .collect(Collectors.toSet());
            assertThat(invalidFields).contains("password");
        }
    }

    private static String[] recordComponents(String className) throws ClassNotFoundException {
        return java.util.Arrays.stream(Class.forName(className).getRecordComponents())
                .map(component -> component.getName())
                .toArray(String[]::new);
    }

    private static Set<String> loadableClasses(String... classNames) {
        return java.util.Arrays.stream(classNames)
                .filter(className -> {
                    try {
                        Class.forName(className);
                        return false;
                    } catch (ClassNotFoundException exception) {
                        return true;
                    }
                })
                .collect(Collectors.toSet());
    }
}
