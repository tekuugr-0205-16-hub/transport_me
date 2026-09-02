package com.mobilityos.common.valueobject;

public record PhoneNumber(String value) {

    public PhoneNumber {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Phone number cannot be empty"
            );
        }

        value = value.trim();
    }

    public static PhoneNumber of(String value) {
        return new PhoneNumber(value);
    }
}
