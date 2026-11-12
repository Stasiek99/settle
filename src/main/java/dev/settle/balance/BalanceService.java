package dev.settle.balance;

import dev.settle.balance.GroupBalancesResponse.CurrencyBalances;
import dev.settle.balance.GroupBalancesResponse.MemberBalance;
import dev.settle.common.Currencies;
import dev.settle.common.ResourceNotFoundException;
import dev.settle.group.Group;
import dev.settle.group.GroupRepository;
import dev.settle.group.MembershipRepository;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class BalanceService {

    private final GroupRepository groups;
    private final MembershipRepository memberships;
    private final BalanceRepository balances;
    private final UserRepository users;

    public BalanceService(GroupRepository groups, MembershipRepository memberships,
                          BalanceRepository balances, UserRepository users) {
        this.groups = groups;
        this.memberships = memberships;
        this.balances = balances;
        this.users = users;
    }

    public GroupBalancesResponse forGroup(UUID groupId) {
        Group group = groups.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", groupId));

        Map<String, Map<UUID, Long>> netByCurrency = new HashMap<>();
        Set<UUID> userIds = new HashSet<>(memberships.findUserIdsByGroupId(groupId));
        for (MemberNetBalance b : balances.findNetBalancesByGroupId(groupId)) {
            netByCurrency.computeIfAbsent(b.getCurrency(), c -> new HashMap<>())
                    .put(b.getUserId(), b.getBalanceMinor());
            userIds.add(b.getUserId());
        }
        Map<UUID, String> names = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));

        List<CurrencyBalances> currencies = Currencies
                .withDefaultFirst(netByCurrency.keySet(), group.getCurrency()).stream()
                .map(currency -> new CurrencyBalances(currency,
                        memberBalances(userIds, names, netByCurrency.getOrDefault(currency, Map.of()))))
                .toList();

        return new GroupBalancesResponse(group.getCurrency(), currencies);
    }

    private static List<MemberBalance> memberBalances(
            Set<UUID> userIds, Map<UUID, String> names, Map<UUID, Long> netByUser) {
        return userIds.stream()
                .map(id -> new MemberBalance(id, names.get(id), netByUser.getOrDefault(id, 0L)))
                .sorted(Comparator.comparingLong(MemberBalance::balance).reversed()
                        .thenComparing(MemberBalance::userId))
                .toList();
    }
}
