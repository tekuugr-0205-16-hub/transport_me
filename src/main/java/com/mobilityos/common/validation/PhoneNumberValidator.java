package com.mobilityos.common.validation;

import com.mobilityos.common.validation.ValidPhoneNumber;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PhoneNumberValidator
        implements ConstraintValidator<ValidPhoneNumber, String> {

    private static final String ETHIOPIAN_PHONE_PATTERN =
            "^(\\+251|251|0)9[0-9]{8}$";

    @Override
    public boolean isValid(
            String value,
            ConstraintValidatorContext context
    ) {

        if (value == null || value.isBlank()) {
            return true;
        }

        return value.trim()
                .matches(ETHIOPIAN_PHONE_PATTERN);
    }
}