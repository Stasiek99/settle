package dev.settle.settlement;

import dev.settle.common.Currencies;
import dev.settle.common.InvalidRequestException;
import dev.settle.common.PageQuery;
import dev.settle.common.PageResponse;
import dev.settle.common.ResourceNotFoundException;
import dev.settle.expense.Money;
import dev.settle.group.Group;
import dev.settle.group.GroupRepository;
import dev.settle.group.MembershipRepository;
import dev.settle.user.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SettlementService {

    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("settledOn"), Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

    private final GroupRepository groups;
    private final MembershipRepository memberships;
    private final SettlementRepository settlements;
    private final UserRepository users;
    private final Clock clock;

    public SettlementService(GroupRepository groups, MembershipRepository memberships,
                             SettlementRepository settlements, UserRepository users, Clock clock) {
        this.groups = groups;
        this.memberships = memberships;
        this.settlements = settlements;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public SettlementResponse create(UUID groupId, CreateSettlementRequest request) {
        Group group = groups.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", groupId));

        Currency currency = Currencies.parse(request.currency());
        if (!currency.getCurrencyCode().equals(group.getCurrency())) {
            throw new InvalidRequestException("Settlement currency %s does not match the group currency %s"
                    .formatted(currency.getCurrencyCode(), group.getCurrency()));
        }
        if (request.paidBy().equals(request.paidTo())) {
            throw new InvalidRequestException("Payer and receiver must be different people");
        }

        Set<UUID> memberIds = memberships.findUserIdsByGroupId(groupId);
        requireMember(memberIds, request.paidBy(), "Payer");
        requireMember(memberIds, request.paidTo(), "Receiver");

        LocalDate date = request.settledOn() != null ? request.settledOn() : LocalDate.now(clock);
        Settlement settlement = settlements.save(new Settlement(
                group,
                users.getReferenceById(request.paidBy()),
                users.getReferenceById(request.paidTo()),
                Money.ofMinor(request.amount(), currency),
                date));
        return SettlementResponse.from(settlement);
    }

    public PageResponse<SettlementResponse> list(UUID groupId, PageQuery query) {
        if (!groups.existsById(groupId)) {
            throw new ResourceNotFoundException("Group", groupId);
        }
        return PageResponse.of(
                settlements.findByGroupId(groupId, query.toPageable(NEWEST_FIRST)), SettlementResponse::from);
    }

    public SettlementResponse get(UUID groupId, UUID settlementId) {
        return settlements.findByIdAndGroupId(settlementId, groupId)
                .map(SettlementResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement", settlementId));
    }

    private static void requireMember(Set<UUID> memberIds, UUID userId, String role) {
        if (!memberIds.contains(userId)) {
            throw new InvalidRequestException(role + " " + userId + " is not a member of this group");
        }
    }
}
