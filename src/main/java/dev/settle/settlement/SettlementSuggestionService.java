package dev.settle.settlement;

import dev.settle.balance.BalanceRepository;
import dev.settle.balance.MemberNetBalance;
import dev.settle.common.Currencies;
import dev.settle.common.ResourceNotFoundException;
import dev.settle.expense.Money;
import dev.settle.group.Group;
import dev.settle.group.GroupRepository;
import dev.settle.settlement.SettlementSuggestionsResponse.CurrencyTransfers;
import dev.settle.settlement.SettlementSuggestionsResponse.Transfer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SettlementSuggestionService {

    private final GroupRepository groups;
    private final BalanceRepository balances;

    public SettlementSuggestionService(GroupRepository groups, BalanceRepository balances) {
        this.groups = groups;
        this.balances = balances;
    }

    public SettlementSuggestionsResponse forGroup(UUID groupId) {
        Group group = groups.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", groupId));

        Map<String, Map<UUID, Money>> netByCurrency = new HashMap<>();
        for (MemberNetBalance b : balances.findNetBalancesByGroupId(groupId)) {
            Currency currency = Currency.getInstance(b.getCurrency());
            netByCurrency.computeIfAbsent(b.getCurrency(), c -> new HashMap<>())
                    .put(b.getUserId(), Money.ofMinor(b.getBalanceMinor(), currency));
        }

        List<CurrencyTransfers> currencies = Currencies
                .withDefaultFirst(netByCurrency.keySet(), group.getCurrency()).stream()
                .map(currency -> new CurrencyTransfers(
                        currency, transfers(netByCurrency.getOrDefault(currency, Map.of()))))
                .toList();
        return new SettlementSuggestionsResponse(group.getCurrency(), currencies);
    }

    private static List<Transfer> transfers(Map<UUID, Money> net) {
        return DebtSimplifier.simplify(net).stream()
                .map(t -> new Transfer(t.paidBy(), t.paidTo(), t.amount().minor()))
                .toList();
    }
}
