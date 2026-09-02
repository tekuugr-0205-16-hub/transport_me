package com.mobilityos.common.util;

public final class ValidationUtils {

    private ValidationUtils() {
    }

    public static void requireNotBlank(
            String value,
            String fieldName
    ) {

        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be blank"
            );
        }
    }

    public static void requirePositive(
            Number value,
            String fieldName
    ) {

        if (value == null || value.doubleValue() <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive"
            );
        }
    }
}