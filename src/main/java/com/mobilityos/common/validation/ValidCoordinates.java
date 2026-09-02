package com.mobilityos.common.validation;

import com.mobilityos.common.validation.validator.CoordinatesValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({
        ElementType.FIELD,
        ElementType.PARAMETER,
        ElementType.TYPE
})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = CoordinatesValidator.class)
public @interface ValidCoordinates {

    String message() default "Invalid coordinates";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}