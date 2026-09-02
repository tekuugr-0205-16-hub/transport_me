package com.mobilityos.common.validation.validator;

import com.mobilityos.common.validation.ValidCoordinates;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CoordinatesValidator
        implements ConstraintValidator<ValidCoordinates, double[]> {

    @Override
    public boolean isValid(
            double[] coordinates,
            ConstraintValidatorContext context
    ) {

        if (coordinates == null) {
            return true;
        }

        if (coordinates.length != 2) {
            return false;
        }

        double latitude = coordinates[0];
        double longitude = coordinates[1];

        return latitude >= -90
                && latitude <= 90
                && longitude >= -180
                && longitude <= 180;
    }
}
