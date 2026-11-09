package dev.settle.settlement;

import java.util.List;
import java.util.UUID;

public record SettlementSuggestionsResponse(String currency, List<Transfer> transfers) {

    public record Transfer(UUID paidBy, UUID paidTo, long amount) {
    }
}
