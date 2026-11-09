package dev.settle.settlement;

import dev.settle.balance.BalanceRepository;
import dev.settle.common.ResourceNotFoundException;
import dev.settle.expense.Money;
import dev.settle.group.Group;
import dev.settle.group.GroupRepository;
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
        Currency currency = Currency.getInstance(group.getCurrency());

        Map<UUID, Money> net = new HashMap<>();
        balances.findNetBalancesByGroupId(groupId)
                .forEach(b -> net.put(b.getUserId(), Money.ofMinor(b.getBalanceMinor(), currency)));

        List<SettlementSuggestionsResponse.Transfer> transfers = DebtSimplifier.simplify(net).stream()
                .map(t -> new SettlementSuggestionsResponse.Transfer(t.paidBy(), t.paidTo(), t.amount().minor()))
                .toList();
        return new SettlementSuggestionsResponse(group.getCurrency(), transfers);
    }
}
