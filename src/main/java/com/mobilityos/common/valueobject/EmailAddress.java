package com.mobilityos.common.valueobject;

public record EmailAddress(String value) {

    public EmailAddress {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Email address cannot be empty"
            );
        }

        value = value.trim().toLowerCase();
    }

    public static EmailAddress of(String value) {
        return new EmailAddress(value);
    }
}
