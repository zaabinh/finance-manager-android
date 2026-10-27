package com.example.fintrack.app.auth.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PasswordValidatorTest {

    @Test
    public void validPassword_returnsTrue() {
        assertTrue(PasswordValidator.isValid("P@ssword123"));
        assertTrue(PasswordValidator.isValid("A1#bcdefgh"));
    }

    @Test
    public void shortPassword_returnsFalse() {
        assertFalse(PasswordValidator.isValid("P@ss1"));
    }

    @Test
    public void missingUppercase_returnsFalse() {
        assertFalse(PasswordValidator.isValid("p@ssword123"));
    }

    @Test
    public void missingLowercase_returnsFalse() {
        assertFalse(PasswordValidator.isValid("P@SSWORD123"));
    }

    @Test
    public void missingDigit_returnsFalse() {
        assertFalse(PasswordValidator.isValid("P@sswordXYZ"));
    }

    @Test
    public void missingSpecialChar_returnsFalse() {
        assertFalse(PasswordValidator.isValid("Password123"));
    }
}
