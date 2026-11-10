package com.fintrack.api;

import com.fintrack.api.service.auth.PasswordPolicy;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PasswordPolicyTest {
    @Test void enforcesComplexityAndBcryptByteLimit() {
        var policy = new PasswordPolicy();
        assertThat(policy.isValid("Strong@123")).isTrue();
        for (String password : new String[]{"short1!", "alllowercase1!", "ALLUPPERCASE1!", "NoDigitsHere!",
                "NoSpecial123", "Whitespace123 ", "Valid@123\n", "A1!" + "é".repeat(36)}) {
            assertThat(policy.isValid(password)).as(password).isFalse();
        }
        assertThat(policy.isValid(null)).isFalse();
    }
}
