package dev.settle.expense;

import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    private static final Currency PLN = Currency.getInstance("PLN");
    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency JPY = Currency.getInstance("JPY");
    private static final Currency KWD = Currency.getInstance("KWD");

    @Test
    void formatsAccordingToTheCurrencyMinorUnits() {
        assertThat(Money.ofMinor(1250, PLN)).hasToString("12.50 PLN");
        assertThat(Money.ofMinor(5, PLN)).hasToString("0.05 PLN");
        assertThat(Money.ofMinor(-1250, PLN)).hasToString("-12.50 PLN");
        assertThat(Money.ofMinor(500, JPY)).hasToString("500 JPY");
        assertThat(Money.ofMinor(1234, KWD)).hasToString("1.234 KWD");
    }

    @Test
    void addsAndSubtractsExactly() {
        Money a = Money.ofMinor(1000, PLN);
        Money b = Money.ofMinor(1, PLN);

        assertThat(a.plus(b)).isEqualTo(Money.ofMinor(1001, PLN));
        assertThat(b.minus(a)).isEqualTo(Money.ofMinor(-999, PLN));
    }

    @Test
    void rejectsMixingCurrencies() {
        assertThatThrownBy(() -> Money.ofMinor(100, PLN).plus(Money.ofMinor(100, EUR)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PLN")
                .hasMessageContaining("EUR");
    }

    @Test
    void failsLoudlyOnOverflowInsteadOfWrappingAround() {
        Money max = Money.ofMinor(Long.MAX_VALUE, PLN);

        assertThatThrownBy(() -> max.plus(Money.ofMinor(1, PLN))).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void rejectsPseudoCurrenciesWithoutMinorUnits() {
        assertThatThrownBy(() -> Money.ofMinor(1, Currency.getInstance("XXX")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportsSign() {
        assertThat(Money.ofMinor(1, PLN).isPositive()).isTrue();
        assertThat(Money.zero(PLN).isPositive()).isFalse();
        assertThat(Money.ofMinor(-1, PLN).isNegative()).isTrue();
    }
}
