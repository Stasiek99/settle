package dev.settle.balance;

import dev.settle.balance.GroupBalancesResponse.MemberBalance;
import dev.settle.common.ResourceNotFoundException;
import dev.settle.group.Group;
import dev.settle.group.GroupRepository;
import dev.settle.group.MembershipRepository;
import dev.settle.support.TestEntities;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    private static final UUID ANN = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);
    private static final UUID CID = new UUID(0, 3);

    private record Net(UUID userId, String currency, long balanceMinor) implements MemberNetBalance {

        Net(UUID userId, long balanceMinor) {
            this(userId, "PLN", balanceMinor);
        }

        @Override
        public UUID getUserId() {
            return userId;
        }

        @Override
        public String getCurrency() {
            return currency;
        }

        @Override
        public long getBalanceMinor() {
            return balanceMinor;
        }
    }

    @Mock
    private GroupRepository groups;

    @Mock
    private MembershipRepository memberships;

    @Mock
    private BalanceRepository balances;

    @Mock
    private UserRepository users;

    @InjectMocks
    private BalanceService service;

    private Group flat;

    @BeforeEach
    void setUp() {
        User ann = TestEntities.withId(new User("ann@example.com", "Ann"), ANN);
        flat = TestEntities.group("Flat", "PLN", ann);
        lenient().when(groups.findById(flat.getId())).thenReturn(Optional.of(flat));
        lenient().when(users.findAllById(any())).thenReturn(List.of(
                TestEntities.withId(new User("ann@example.com", "Ann"), ANN),
                TestEntities.withId(new User("bob@example.com", "Bob"), BOB),
                TestEntities.withId(new User("cid@example.com", "Cid"), CID)));
    }

    @Test
    void listsEveryMemberIncludingThoseWithoutAnyActivity() {
        when(memberships.findUserIdsByGroupId(flat.getId())).thenReturn(Set.of(ANN, BOB, CID));
        when(balances.findNetBalancesByGroupId(flat.getId())).thenReturn(List.of(new Net(ANN, 500), new Net(BOB, -500)));

        GroupBalancesResponse response = service.forGroup(flat.getId());

        assertThat(response.currencies().get(0).balances()).containsExactly(
                new MemberBalance(ANN, "Ann", 500),
                new MemberBalance(CID, "Cid", 0),
                new MemberBalance(BOB, "Bob", -500));
    }

    @Test
    void sortsCreditorsFirstAndBreaksTiesByUserId() {
        when(memberships.findUserIdsByGroupId(flat.getId())).thenReturn(Set.of(ANN, BOB, CID));
        when(balances.findNetBalancesByGroupId(flat.getId()))
                .thenReturn(List.of(new Net(CID, -100), new Net(BOB, -100), new Net(ANN, 200)));

        GroupBalancesResponse response = service.forGroup(flat.getId());

        assertThat(response.currencies().get(0).balances()).extracting(MemberBalance::userId).containsExactly(ANN, BOB, CID);
    }

    @Test
    void includesAUserWithABalanceWhoIsNoLongerAMember() {
        when(memberships.findUserIdsByGroupId(flat.getId())).thenReturn(Set.of(ANN));
        when(balances.findNetBalancesByGroupId(flat.getId())).thenReturn(List.of(new Net(ANN, 300), new Net(BOB, -300)));

        GroupBalancesResponse response = service.forGroup(flat.getId());

        assertThat(response.currencies().get(0).balances()).extracting(MemberBalance::userId).containsExactly(ANN, BOB);
    }

    @Test
    void keepsEachCurrencyInItsOwnSectionWithTheGroupCurrencyFirst() {
        when(memberships.findUserIdsByGroupId(flat.getId())).thenReturn(Set.of(ANN, BOB));
        when(balances.findNetBalancesByGroupId(flat.getId())).thenReturn(List.of(
                new Net(ANN, "USD", 700), new Net(BOB, "USD", -700),
                new Net(ANN, "EUR", -200), new Net(BOB, "EUR", 200),
                new Net(ANN, "PLN", 500), new Net(BOB, "PLN", -500)));

        GroupBalancesResponse response = service.forGroup(flat.getId());

        assertThat(response.currencies()).extracting(GroupBalancesResponse.CurrencyBalances::currency)
                .containsExactly("PLN", "EUR", "USD");
        assertThat(response.currencies().get(1).balances()).containsExactly(
                new MemberBalance(BOB, "Bob", 200), new MemberBalance(ANN, "Ann", -200));
        assertThat(response.currencies().get(2).balances()).containsExactly(
                new MemberBalance(ANN, "Ann", 700), new MemberBalance(BOB, "Bob", -700));
    }

    @Test
    void alwaysIncludesTheGroupCurrencyEvenWithoutActivityInIt() {
        when(memberships.findUserIdsByGroupId(flat.getId())).thenReturn(Set.of(ANN, BOB));
        when(balances.findNetBalancesByGroupId(flat.getId()))
                .thenReturn(List.of(new Net(ANN, "EUR", 100), new Net(BOB, "EUR", -100)));

        GroupBalancesResponse response = service.forGroup(flat.getId());

        assertThat(response.currencies()).extracting(GroupBalancesResponse.CurrencyBalances::currency)
                .containsExactly("PLN", "EUR");
        assertThat(response.currencies().get(0).balances()).extracting(MemberBalance::balance)
                .containsOnly(0L);
    }

    @Test
    void reportsTheGroupCurrency() {
        when(memberships.findUserIdsByGroupId(flat.getId())).thenReturn(Set.of());
        when(balances.findNetBalancesByGroupId(flat.getId())).thenReturn(List.of());

        assertThat(service.forGroup(flat.getId()).defaultCurrency()).isEqualTo("PLN");
    }

    @Test
    void unknownGroupIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(groups.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.forGroup(unknown)).isInstanceOf(ResourceNotFoundException.class);
    }
}
