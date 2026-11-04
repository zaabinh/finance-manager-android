package com.fintrack.api.dto.request.auth;

import com.fintrack.api.service.auth.PasswordPolicy;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordPolicyValidator implements ConstraintValidator<ValidPassword, String> {
    private final PasswordPolicy policy = new PasswordPolicy();
    @Override public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isBlank() || policy.isValid(value);
    }
}
