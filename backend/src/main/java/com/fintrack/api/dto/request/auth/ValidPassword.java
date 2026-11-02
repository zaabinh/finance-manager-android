package com.fintrack.api.dto.request.auth;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordPolicyValidator.class)
public @interface ValidPassword {
    String message() default "PASSWORD_POLICY_INVALID";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
