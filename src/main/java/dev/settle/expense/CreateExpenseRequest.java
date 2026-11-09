package dev.settle.expense;

import dev.settle.common.NoControlCharacters;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateExpenseRequest(
        @NotBlank @Size(max = 200) @NoControlCharacters String description,
        @NotNull @Positive @Max(MAX_AMOUNT_MINOR) Long amount,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a 3-letter currency code") String currency,
        @NotNull UUID paidBy,
        @NotNull SplitType splitType,
        LocalDate expenseDate,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid Participant> participants
) {

    public static final long MAX_AMOUNT_MINOR = 1_000_000_000_000L;

    public record Participant(
            @NotNull UUID userId,
            @Min(0) @Max(10_000) Integer basisPoints,
            @PositiveOrZero @Max(MAX_AMOUNT_MINOR) Long amount
    ) {
    }
}
