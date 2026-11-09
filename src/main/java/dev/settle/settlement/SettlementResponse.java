package dev.settle.settlement;

import dev.settle.expense.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SettlementResponse(
        UUID id,
        UUID groupId,
        UUID paidBy,
        UUID paidTo,
        long amount,
        String currency,
        LocalDate settledOn,
        Instant createdAt
) {

    public static SettlementResponse from(Settlement settlement) {
        Money amount = settlement.getAmount();
        return new SettlementResponse(
                settlement.getId(),
                settlement.getGroup().getId(),
                settlement.getPaidBy().getId(),
                settlement.getPaidTo().getId(),
                amount.minor(),
                amount.currency().getCurrencyCode(),
                settlement.getSettledOn(),
                settlement.getCreatedAt());
    }
}
