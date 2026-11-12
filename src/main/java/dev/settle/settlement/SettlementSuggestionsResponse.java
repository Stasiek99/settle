package dev.settle.settlement;

import java.util.List;
import java.util.UUID;

public record SettlementSuggestionsResponse(String defaultCurrency, List<CurrencyTransfers> currencies) {

    public record CurrencyTransfers(String currency, List<Transfer> transfers) {
    }

    public record Transfer(UUID paidBy, UUID paidTo, long amount) {
    }
}
