package dev.settle.expense;

import dev.settle.common.InvalidRequestException;
import dev.settle.common.ResourceNotFoundException;
import dev.settle.expense.CreateExpenseRequest.Participant;
import dev.settle.group.Group;
import dev.settle.cache.GroupCacheEvictor;
import dev.settle.group.GroupAccess;
import dev.settle.group.GroupRepository;
import dev.settle.group.MembershipRepository;
import dev.settle.support.TestEntities;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    private static final UUID ANN = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);
    private static final UUID CID = new UUID(0, 3);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    @Mock
    private GroupRepository groups;

    @Mock
    private MembershipRepository memberships;

    @Mock
    private ExpenseRepository expenses;

    @Mock
    private UserRepository users;

    @Mock
    private GroupAccess access;

    @Mock
    private GroupCacheEvictor cacheEvictor;

    private ExpenseService service;
    private Group flat;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC);
        service = new ExpenseService(groups, memberships, expenses, users, access, cacheEvictor, clock);

        User ann = TestEntities.withId(new User("ann@example.com", "Ann"), ANN);
        flat = TestEntities.group("Flat", "PLN", ann);

        lenient().when(groups.findByIdForUpdate(flat.getId())).thenReturn(Optional.of(flat));
        lenient().when(memberships.findUserIdsByGroupId(flat.getId())).thenReturn(Set.of(ANN, BOB, CID));
        lenient().when(users.getReferenceById(any(UUID.class)))
                .thenAnswer(inv -> TestEntities.withId(new User("x@example.com", "X"), inv.getArgument(0)));
        lenient().when(expenses.save(any(Expense.class)))
                .thenAnswer(inv -> TestEntities.withId(inv.getArgument(0)));
    }

    private void setCursor(long cursor) {
        ReflectionTestUtils.setField(flat, "rotationCursor", cursor);
    }

    private static List<Participant> equalBetween(UUID... ids) {
        List<Participant> list = new ArrayList<>();
        for (UUID id : ids) {
            list.add(new Participant(id, null, null));
        }
        return list;
    }

    private static CreateExpenseRequest equalSplit(long amount, List<Participant> participants) {
        return new CreateExpenseRequest("Pizza", amount, "PLN", ANN, SplitType.EQUAL, null, participants);
    }

    private static List<Long> amounts(ExpenseResponse response) {
        return response.splits().stream().map(ExpenseResponse.SplitResponse::amount).toList();
    }

    @Test
    void evictsTheCachedGroupDataWhenAnExpenseIsCreated() {
        service.create(flat.getId(), equalSplit(1000, equalBetween(ANN, BOB)));

        verify(cacheEvictor).evictGroupData(flat.getId());
    }

    @Test
    void rejectedExpensesLeaveTheCacheAlone() {
        assertThatThrownBy(() -> service.create(flat.getId(), equalSplit(1000, equalBetween(ANN, ANN))))
                .isInstanceOf(InvalidRequestException.class);

        verify(cacheEvictor, never()).evictGroupData(any());
    }

    @Test
    void equalSplitGivesTheExtraGroszToTheParticipantAtTheCursor() {
        ExpenseResponse response = service.create(flat.getId(), equalSplit(1000, equalBetween(ANN, BOB, CID)));

        assertThat(amounts(response)).containsExactly(334L, 333L, 333L);
        assertThat(response.splits()).extracting(ExpenseResponse.SplitResponse::userId)
                .containsExactly(ANN, BOB, CID);
        verify(groups).advanceRotationCursor(flat.getId(), 1);
    }

    @Test
    void theCursorDecidesWhoPaysTheExtraGrosz() {
        setCursor(1);

        ExpenseResponse response = service.create(flat.getId(), equalSplit(1000, equalBetween(ANN, BOB, CID)));

        assertThat(amounts(response)).containsExactly(333L, 334L, 333L);
    }

    @Test
    void anEvenlyDivisibleExpenseLeavesTheCursorAlone() {
        service.create(flat.getId(), equalSplit(900, equalBetween(ANN, BOB, CID)));

        verify(groups, never()).advanceRotationCursor(any(), anyLong());
    }

    @Test
    void theOrderOfParticipantsInTheRequestDoesNotMatter() {
        setCursor(2);
        ExpenseResponse inOrder = service.create(flat.getId(), equalSplit(1000, equalBetween(ANN, BOB, CID)));
        ExpenseResponse reversed = service.create(flat.getId(), equalSplit(1000, equalBetween(CID, BOB, ANN)));

        assertThat(amounts(reversed)).isEqualTo(amounts(inOrder)).containsExactly(333L, 333L, 334L);
    }

    @Test
    void splitsByPercentage() {
        List<Participant> participants = List.of(new Participant(ANN, 5000, null), new Participant(BOB, 5000, null));
        var request = new CreateExpenseRequest("Taxi", 101L, "PLN", BOB, SplitType.PERCENTAGE, null, participants);

        ExpenseResponse response = service.create(flat.getId(), request);

        assertThat(amounts(response)).containsExactly(51L, 50L);
        verify(groups).advanceRotationCursor(flat.getId(), 1);
    }

    @Test
    void splitsByExactAmountsWithoutTouchingTheCursor() {
        List<Participant> participants = List.of(new Participant(ANN, null, 3000L), new Participant(BOB, null, 2000L));
        var request = new CreateExpenseRequest("Dinner", 5000L, "PLN", ANN, SplitType.EXACT, null, participants);

        ExpenseResponse response = service.create(flat.getId(), request);

        assertThat(amounts(response)).containsExactly(3000L, 2000L);
        verify(groups, never()).advanceRotationCursor(any(), anyLong());
    }

    @Test
    void savesTheExpenseWithPayerAmountAndType() {
        service.create(flat.getId(), new CreateExpenseRequest(
                "  Pizza ", 1000L, "pln", BOB, SplitType.EQUAL, null, equalBetween(ANN, BOB)));

        ArgumentCaptor<Expense> saved = ArgumentCaptor.forClass(Expense.class);
        verify(expenses).save(saved.capture());
        Expense expense = saved.getValue();
        assertThat(expense.getPaidBy().getId()).isEqualTo(BOB);
        assertThat(expense.getDescription()).isEqualTo("Pizza");
        assertThat(expense.getAmount()).isEqualTo(Money.ofMinor(1000, java.util.Currency.getInstance("PLN")));
        assertThat(expense.getSplitType()).isEqualTo(SplitType.EQUAL);
        assertThat(expense.getSplits()).hasSize(2);
    }

    @Test
    void defaultsTheDateToTodayAndKeepsAGivenOne() {
        ExpenseResponse withoutDate = service.create(flat.getId(), equalSplit(1000, equalBetween(ANN, BOB)));
        ExpenseResponse withDate = service.create(flat.getId(), new CreateExpenseRequest(
                "Old", 1000L, "PLN", ANN, SplitType.EQUAL, LocalDate.of(2026, 1, 15), equalBetween(ANN, BOB)));

        assertThat(withoutDate.expenseDate()).isEqualTo(TODAY);
        assertThat(withDate.expenseDate()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    @Test
    void unknownGroupIsNotFound() {
        UUID unknown = UUID.randomUUID();
        lenient().when(groups.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(unknown, equalSplit(1000, equalBetween(ANN, BOB))))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(expenses, never()).save(any());
    }

    @Test
    void acceptsACurrencyDifferentFromTheGroupsAndSplitsInThatCurrency() {
        var request = new CreateExpenseRequest("Hotel", 1001L, "eur", ANN, SplitType.EQUAL, null,
                equalBetween(ANN, BOB));

        ExpenseResponse response = service.create(flat.getId(), request);

        assertThat(response.currency()).isEqualTo("EUR");
        assertThat(amounts(response)).containsExactly(501L, 500L);
    }

    @Test
    void rejectsAnUnknownCurrencyCode() {
        var request = new CreateExpenseRequest("X", 1000L, "ZZZ", ANN, SplitType.EQUAL, null, equalBetween(ANN));

        assertRejected(request, "Unknown currency");
    }

    @Test
    void rejectsAPayerWhoIsNotInTheGroup() {
        UUID stranger = UUID.randomUUID();
        var request = new CreateExpenseRequest("X", 1000L, "PLN", stranger, SplitType.EQUAL, null, equalBetween(ANN));

        assertRejected(request, "Payer " + stranger + " is not a member");
    }

    @Test
    void rejectsAParticipantWhoIsNotInTheGroup() {
        UUID stranger = UUID.randomUUID();

        assertRejected(equalSplit(1000, equalBetween(ANN, stranger)), "Participant " + stranger + " is not a member");
    }

    @Test
    void rejectsTheSameParticipantTwice() {
        assertRejected(equalSplit(1000, equalBetween(ANN, ANN)), "only once");
    }

    @Test
    void equalSplitRejectsAmountsAndPercentages() {
        List<Participant> participants = List.of(new Participant(ANN, 5000, null), new Participant(BOB, null, null));

        assertRejected(equalSplit(1000, participants), "EQUAL");
    }

    @Test
    void percentageSplitRejectsAMissingPercentage() {
        List<Participant> participants = List.of(new Participant(ANN, 10000, null), new Participant(BOB, null, null));
        var request = new CreateExpenseRequest("X", 1000L, "PLN", ANN, SplitType.PERCENTAGE, null, participants);

        assertRejected(request, "PERCENTAGE");
    }

    @Test
    void percentageSplitRejectsPercentagesNotAddingUpTo100() {
        List<Participant> participants = List.of(new Participant(ANN, 5000, null), new Participant(BOB, 4000, null));
        var request = new CreateExpenseRequest("X", 1000L, "PLN", ANN, SplitType.PERCENTAGE, null, participants);

        assertRejected(request, "100%");
    }

    @Test
    void exactSplitRejectsAmountsThatDoNotAddUp() {
        List<Participant> participants = List.of(new Participant(ANN, null, 3000L), new Participant(BOB, null, 1000L));
        var request = new CreateExpenseRequest("X", 5000L, "PLN", ANN, SplitType.EXACT, null, participants);

        assertRejected(request, "40.00 PLN");
    }

    @Test
    void exactSplitRejectsAMissingAmount() {
        List<Participant> participants = Collections.singletonList(new Participant(ANN, null, null));
        var request = new CreateExpenseRequest("X", 5000L, "PLN", ANN, SplitType.EXACT, null, participants);

        assertRejected(request, "EXACT");
    }

    private void assertRejected(CreateExpenseRequest request, String messagePart) {
        assertThatThrownBy(() -> service.create(flat.getId(), request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining(messagePart);

        verify(expenses, never()).save(any());
        verify(groups, never()).advanceRotationCursor(any(), anyLong());
    }
}
