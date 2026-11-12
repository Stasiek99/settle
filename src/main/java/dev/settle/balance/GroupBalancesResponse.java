package dev.settle.balance;

import java.util.List;
import java.util.UUID;

public record GroupBalancesResponse(String defaultCurrency, List<CurrencyBalances> currencies) {

    public record CurrencyBalances(String currency, List<MemberBalance> balances) {
    }

    public record MemberBalance(UUID userId, String displayName, long balance) {
    }
}
