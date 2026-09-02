package com.mobilityos.common.valueobject;

public record Address(
        String country,
        String region,
        String city,
        String subCity,
        String woreda,
        String street,
        String description
) {
}
