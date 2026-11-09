package dev.settle.balance;

import dev.settle.balance.GroupBalancesResponse.MemberBalance;
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

        Map<UUID, Long> netByUser = new HashMap<>();
        balances.findNetBalancesByGroupId(groupId)
                .forEach(b -> netByUser.put(b.getUserId(), b.getBalanceMinor()));

        Set<UUID> userIds = new HashSet<>(memberships.findUserIdsByGroupId(groupId));
        userIds.addAll(netByUser.keySet());
        Map<UUID, String> names = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));

        List<MemberBalance> result = userIds.stream()
                .map(id -> new MemberBalance(id, names.get(id), netByUser.getOrDefault(id, 0L)))
                .sorted(Comparator.comparingLong(MemberBalance::balance).reversed()
                        .thenComparing(MemberBalance::userId))
                .toList();

        return new GroupBalancesResponse(group.getCurrency(), result);
    }
}
