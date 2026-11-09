package dev.settle.common;

import java.util.Currency;
import java.util.Locale;

public final class Currencies {

    private Currencies() {
    }

    public static Currency parse(String code) {
        try {
            return Currency.getInstance(code.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Unknown currency " + code);
        }
    }
}
