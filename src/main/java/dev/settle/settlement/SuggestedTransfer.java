package dev.settle.settlement;

import dev.settle.expense.Money;

import java.util.UUID;

public record SuggestedTransfer(UUID paidBy, UUID paidTo, Money amount) {
}
