package dev.settle.common;

import java.util.Comparator;
import java.util.Currency;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

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

    public static Set<String> withDefaultFirst(Set<String> used, String defaultCurrency) {
        Set<String> all = new TreeSet<>(Comparator
                .comparing((String code) -> !code.equals(defaultCurrency))
                .thenComparing(Comparator.naturalOrder()));
        all.addAll(used);
        all.add(defaultCurrency);
        return all;
    }
}
