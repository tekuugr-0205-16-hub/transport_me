package com.mobilityos.common.valueobject;

import java.math.BigDecimal;
import java.util.Objects;

public record Money(
        BigDecimal amount,
        String currency
) {

    public Money {
        Objects.requireNonNull(amount, "Amount cannot be null");
        Objects.requireNonNull(currency, "Currency cannot be null");

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Amount cannot be negative"
            );
        }

        currency = currency.trim().toUpperCase();
    }

    public static Money etb(BigDecimal amount) {
        return new Money(amount, "ETB");
    }

    public static Money zero(String currency) {
        return new Money(
                BigDecimal.ZERO,
                currency
        );
    }
}