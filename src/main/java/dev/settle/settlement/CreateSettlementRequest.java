package dev.settle.settlement;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.UUID;

public record CreateSettlementRequest(
        @NotNull UUID paidBy,
        @NotNull UUID paidTo,
        @NotNull @Positive @Max(MAX_AMOUNT_MINOR) Long amount,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a 3-letter currency code") String currency,
        LocalDate settledOn
) {

    public static final long MAX_AMOUNT_MINOR = 1_000_000_000_000L;
}
