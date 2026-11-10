package dev.settle.expense;

import dev.settle.common.Currencies;
import dev.settle.common.InvalidRequestException;
import dev.settle.common.PageQuery;
import dev.settle.common.PageResponse;
import dev.settle.common.ResourceNotFoundException;
import dev.settle.expense.CreateExpenseRequest.Participant;
import dev.settle.group.Group;
import dev.settle.group.GroupRepository;
import dev.settle.group.MembershipRepository;
import dev.settle.user.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ExpenseService {

    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("expenseDate"), Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

    private final GroupRepository groups;
    private final MembershipRepository memberships;
    private final ExpenseRepository expenses;
    private final UserRepository users;
    private final Clock clock;

    public ExpenseService(GroupRepository groups, MembershipRepository memberships,
                          ExpenseRepository expenses, UserRepository users, Clock clock) {
        this.groups = groups;
        this.memberships = memberships;
        this.expenses = expenses;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public ExpenseResponse create(UUID groupId, CreateExpenseRequest request) {
        Group group = groups.findByIdForUpdate(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", groupId));

        Currency currency = Currencies.parse(request.currency());
        if (!currency.getCurrencyCode().equals(group.getCurrency())) {
            throw new InvalidRequestException("Expense currency %s does not match the group currency %s"
                    .formatted(currency.getCurrencyCode(), group.getCurrency()));
        }

        List<Participant> participants = request.participants().stream()
                .sorted(Comparator.comparing(Participant::userId))
                .toList();
        requireUnique(participants);

        Set<UUID> memberIds = memberships.findUserIdsByGroupId(groupId);
        requireMember(memberIds, request.paidBy(), "Payer");
        participants.forEach(p -> requireMember(memberIds, p.userId(), "Participant"));

        Money total = Money.ofMinor(request.amount(), currency);
        SplitResult split = split(request.splitType(), total, participants, group.getRotationCursor());

        LocalDate date = request.expenseDate() != null ? request.expenseDate() : LocalDate.now(clock);
        Expense expense = new Expense(group, users.getReferenceById(request.paidBy()),
                request.description(), total, request.splitType(), date);
        split.shares().forEach((userId, share) -> expense.addSplit(users.getReferenceById(userId), share));
        expense.requireSplitsAddUp();

        expenses.save(expense);
        if (split.leftoverUnits() > 0) {
            groups.advanceRotationCursor(groupId, split.leftoverUnits());
        }
        return ExpenseResponse.from(expense);
    }

    public PageResponse<ExpenseResponse> list(UUID groupId, PageQuery query) {
        if (!groups.existsById(groupId)) {
            throw new ResourceNotFoundException("Group", groupId);
        }
        return PageResponse.of(expenses.findByGroupId(groupId, query.toPageable(NEWEST_FIRST)), ExpenseResponse::from);
    }

    public ExpenseResponse get(UUID groupId, UUID expenseId) {
        return expenses.findByIdAndGroupId(expenseId, groupId)
                .map(ExpenseResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Expense", expenseId));
    }

    private SplitResult split(SplitType type, Money total, List<Participant> participants, long cursor) {
        try {
            return switch (type) {
                case EQUAL -> {
                    for (Participant p : participants) {
                        if (p.basisPoints() != null || p.amount() != null) {
                            throw new InvalidRequestException(
                                    "An EQUAL split takes only user ids, without percentages or amounts");
                        }
                    }
                    yield SplitCalculator.splitEqually(
                            total, participants.stream().map(Participant::userId).toList(), cursor);
                }
                case PERCENTAGE -> {
                    Map<UUID, Integer> basisPoints = new LinkedHashMap<>();
                    for (Participant p : participants) {
                        if (p.basisPoints() == null || p.amount() != null) {
                            throw new InvalidRequestException(
                                    "A PERCENTAGE split needs basisPoints (and no amount) for every participant");
                        }
                        basisPoints.put(p.userId(), p.basisPoints());
                    }
                    yield SplitCalculator.splitByPercentage(total, basisPoints, cursor);
                }
                case EXACT -> {
                    Map<UUID, Money> amounts = new LinkedHashMap<>();
                    for (Participant p : participants) {
                        if (p.amount() == null || p.basisPoints() != null) {
                            throw new InvalidRequestException(
                                    "An EXACT split needs an amount (and no basisPoints) for every participant");
                        }
                        amounts.put(p.userId(), Money.ofMinor(p.amount(), total.currency()));
                    }
                    yield SplitCalculator.splitByExactAmounts(total, amounts);
                }
            };
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(e.getMessage());
        }
    }

    private static void requireUnique(List<Participant> participants) {
        if (participants.stream().map(Participant::userId).distinct().count() != participants.size()) {
            throw new InvalidRequestException("Each participant may appear only once");
        }
    }

    private static void requireMember(Set<UUID> memberIds, UUID userId, String role) {
        if (!memberIds.contains(userId)) {
            throw new InvalidRequestException(role + " " + userId + " is not a member of this group");
        }
    }
}
