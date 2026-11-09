package dev.settle.expense;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        UUID groupId,
        UUID paidBy,
        String description,
        long amount,
        String currency,
        SplitType splitType,
        LocalDate expenseDate,
        Instant createdAt,
        List<SplitResponse> splits
) {

    public record SplitResponse(UUID userId, long amount) {
    }

    public static ExpenseResponse from(Expense expense) {
        List<SplitResponse> splits = expense.getSplits().stream()
                .map(s -> new SplitResponse(s.getUser().getId(), s.getAmountMinor()))
                .sorted(Comparator.comparing(SplitResponse::userId))
                .toList();

        Money amount = expense.getAmount();
        return new ExpenseResponse(
                expense.getId(),
                expense.getGroup().getId(),
                expense.getPaidBy().getId(),
                expense.getDescription(),
                amount.minor(),
                amount.currency().getCurrencyCode(),
                expense.getSplitType(),
                expense.getExpenseDate(),
                expense.getCreatedAt(),
                splits);
    }
}
