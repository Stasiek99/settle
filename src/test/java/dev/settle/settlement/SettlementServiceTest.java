package dev.settle.settlement;

import dev.settle.cache.GroupCacheEvictor;
import dev.settle.common.InvalidRequestException;
import dev.settle.common.ResourceNotFoundException;
import dev.settle.expense.Money;
import dev.settle.group.Group;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    private static final UUID ANN = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);

    @Mock
    private GroupRepository groups;

    @Mock
    private MembershipRepository memberships;

    @Mock
    private SettlementRepository settlements;

    @Mock
    private UserRepository users;

    @Mock
    private GroupCacheEvictor cacheEvictor;

    private SettlementService service;
    private Group flat;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC);
        service = new SettlementService(groups, memberships, settlements, users, cacheEvictor, clock);

        User ann = TestEntities.withId(new User("ann@example.com", "Ann"), ANN);
        flat = TestEntities.group("Flat", "PLN", ann);

        lenient().when(groups.findById(flat.getId())).thenReturn(Optional.of(flat));
        lenient().when(memberships.findUserIdsByGroupId(flat.getId())).thenReturn(Set.of(ANN, BOB));
        lenient().when(users.getReferenceById(any(UUID.class)))
                .thenAnswer(inv -> TestEntities.withId(new User("x@example.com", "X"), inv.getArgument(0)));
        lenient().when(settlements.save(any(Settlement.class)))
                .thenAnswer(inv -> TestEntities.withId(inv.getArgument(0)));
    }

    private CreateSettlementRequest request(UUID paidBy, UUID paidTo, long amount, String currency) {
        return new CreateSettlementRequest(paidBy, paidTo, amount, currency, null);
    }

    @Test
    void savesTheSettlementAndReturnsIt() {
        SettlementResponse response = service.create(flat.getId(), request(BOB, ANN, 1500, "pln"));

        ArgumentCaptor<Settlement> saved = ArgumentCaptor.forClass(Settlement.class);
        verify(settlements).save(saved.capture());
        assertThat(saved.getValue().getPaidBy().getId()).isEqualTo(BOB);
        assertThat(saved.getValue().getPaidTo().getId()).isEqualTo(ANN);
        assertThat(saved.getValue().getAmount()).isEqualTo(Money.ofMinor(1500, Currency.getInstance("PLN")));
        assertThat(response.paidBy()).isEqualTo(BOB);
        assertThat(response.paidTo()).isEqualTo(ANN);
        assertThat(response.amount()).isEqualTo(1500);
        assertThat(response.currency()).isEqualTo("PLN");
    }

    @Test
    void evictsTheCachedGroupDataOnceTheSettlementIsSaved() {
        service.create(flat.getId(), request(BOB, ANN, 100, "PLN"));

        verify(cacheEvictor).evictGroupData(flat.getId());
    }

    @Test
    void defaultsTheDateToTodayAndKeepsAGivenOne() {
        SettlementResponse withoutDate = service.create(flat.getId(), request(BOB, ANN, 100, "PLN"));
        SettlementResponse withDate = service.create(flat.getId(),
                new CreateSettlementRequest(BOB, ANN, 100L, "PLN", LocalDate.of(2026, 1, 15)));

        assertThat(withoutDate.settledOn()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(withDate.settledOn()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    @Test
    void unknownGroupIsNotFound() {
        UUID unknown = UUID.randomUUID();
        lenient().when(groups.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(unknown, request(BOB, ANN, 100, "PLN")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsPayingYourself() {
        assertRejected(request(ANN, ANN, 100, "PLN"), "different people");
    }

    @Test
    void rejectsAPayerWhoIsNotInTheGroup() {
        UUID stranger = UUID.randomUUID();

        assertRejected(request(stranger, ANN, 100, "PLN"), "Payer " + stranger + " is not a member");
    }

    @Test
    void rejectsAReceiverWhoIsNotInTheGroup() {
        UUID stranger = UUID.randomUUID();

        assertRejected(request(ANN, stranger, 100, "PLN"), "Receiver " + stranger + " is not a member");
    }

    @Test
    void acceptsACurrencyDifferentFromTheGroups() {
        SettlementResponse response = service.create(flat.getId(), request(BOB, ANN, 100, "eur"));

        assertThat(response.currency()).isEqualTo("EUR");
    }

    @Test
    void rejectsAnUnknownCurrencyCode() {
        assertRejected(request(BOB, ANN, 100, "ZZZ"), "Unknown currency");
    }

    private void assertRejected(CreateSettlementRequest request, String messagePart) {
        assertThatThrownBy(() -> service.create(flat.getId(), request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining(messagePart);

        verify(settlements, never()).save(any());
    }
}
