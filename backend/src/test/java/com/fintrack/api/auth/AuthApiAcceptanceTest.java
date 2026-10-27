package com.fintrack.api.auth;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
@DisplayName("Authentication API acceptance criteria")
class AuthApiAcceptanceTest {

    private static final String REGISTER_PATH = "/api/v1/auth/register";
    private static final String LOGIN_PATH = "/api/v1/auth/login";

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("Register")
    class Register {

        @Test
        void acceptsAnonymousRegistrationAndReturnsCreatedWithoutBody() throws Exception {
            mockMvc.perform(post(REGISTER_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "an.nguyen",
                                      "email": "an@example.com",
                                      "password": "Strong@123"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(content().string(emptyOrNullString()));
        }

        @Test
        void rejectsMissingRequiredFieldsWithFieldLevelErrors() throws Exception {
            mockMvc.perform(post(REGISTER_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.path").value(REGISTER_PATH))
                    .andExpect(jsonPath("$.fieldErrors[*].field",
                            containsInAnyOrder("username", "email", "password")))
                    .andExpect(jsonPath("$.timestamp", not(emptyOrNullString())));
        }

        @Test
        void rejectsMalformedEmail() throws Exception {
            assertRegisterFieldError("""
                    {
                      "username": "an.nguyen",
                      "email": "not-an-email",
                      "password": "Strong@123"
                    }
                    """, "email");
        }

        @Test
        void rejectsPasswordWithoutRequiredComplexity() throws Exception {
            assertRegisterFieldError("""
                    {
                      "username": "an.nguyen",
                      "email": "an@example.com",
                      "password": "onlylowercase"
                    }
                    """, "password");
        }

        @Test
        void rejectsMalformedJsonWithoutLeakingSubmittedSecrets() throws Exception {
            mockMvc.perform(post(REGISTER_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"Strong@123\""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.path").value(REGISTER_PATH))
                    .andExpect(content().string(not(org.hamcrest.Matchers.containsString("Strong@123"))))
                    .andExpect(jsonPath("$.stackTrace").doesNotExist());
        }

        private void assertRegisterFieldError(String body, String field) throws Exception {
            mockMvc.perform(post(REGISTER_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == '%s')]", field).isNotEmpty());
        }
    }

    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        void rejectsBlankCredentialsWithFieldLevelErrors() throws Exception {
            mockMvc.perform(post(LOGIN_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "usernameOrEmail": " ",
                                      "password": " "
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fieldErrors[*].field",
                            containsInAnyOrder("usernameOrEmail", "password")));
        }

        @Test
        void usesTheSameErrorForUnknownIdentifierAndWrongPassword() throws Exception {
            assertInvalidCredentials("missing@example.com", "Strong@123");
            assertInvalidCredentials("an@example.com", "DefinitelyWrong@123");
        }

        private void assertInvalidCredentials(String identifier, String password) throws Exception {
            mockMvc.perform(post(LOGIN_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "usernameOrEmail": "%s",
                                      "password": "%s"
                                    }
                                    """.formatted(identifier, password)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("AUTH_INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.message").value("Invalid credentials"))
                    .andExpect(jsonPath("$.path").value(LOGIN_PATH))
                    .andExpect(jsonPath("$.stackTrace").doesNotExist())
                    .andExpect(content().string(not(org.hamcrest.Matchers.containsString(password))));
        }
    }
}
