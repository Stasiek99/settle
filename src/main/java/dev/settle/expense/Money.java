package dev.settle.expense;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

public record Money(long minor, Currency currency) {

    public Money {
        Objects.requireNonNull(currency, "currency");
        if (currency.getDefaultFractionDigits() < 0) {
            throw new IllegalArgumentException("Currency has no minor units: " + currency.getCurrencyCode());
        }
    }

    public static Money ofMinor(long minor, Currency currency) {
        return new Money(minor, currency);
    }

    public static Money zero(Currency currency) {
        return new Money(0, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(minor, other.minor), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(minor, other.minor), currency);
    }

    public boolean isPositive() {
        return minor > 0;
    }

    public boolean isNegative() {
        return minor < 0;
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: " + currency.getCurrencyCode() + " vs " + other.currency.getCurrencyCode());
        }
    }

    @Override
    public String toString() {
        return BigDecimal.valueOf(minor, currency.getDefaultFractionDigits()).toPlainString()
                + " " + currency.getCurrencyCode();
    }
}
