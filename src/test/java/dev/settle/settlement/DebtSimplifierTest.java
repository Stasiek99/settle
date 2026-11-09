package dev.settle.settlement;

import dev.settle.expense.Money;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DebtSimplifierTest {

    private static final Currency PLN = Currency.getInstance("PLN");

    private static Money pln(long minor) {
        return Money.ofMinor(minor, PLN);
    }

    private static UUID person(int number) {
        return new UUID(0, number);
    }

    private static Map<UUID, Money> balances(long... values) {
        Map<UUID, Money> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i++) {
            map.put(person(i + 1), pln(values[i]));
        }
        return map;
    }

    private static Map<UUID, Long> afterApplying(Map<UUID, Money> balances, List<SuggestedTransfer> transfers) {
        Map<UUID, Long> after = new HashMap<>();
        balances.forEach((id, money) -> after.put(id, money.minor()));
        for (SuggestedTransfer t : transfers) {
            after.merge(t.paidBy(), t.amount().minor(), Long::sum);
            after.merge(t.paidTo(), -t.amount().minor(), Long::sum);
        }
        return after;
    }

    private static void assertSettlesEverything(Map<UUID, Money> balances, List<SuggestedTransfer> transfers) {
        assertThat(afterApplying(balances, transfers).values()).containsOnly(0L);
        assertThat(transfers).allSatisfy(t -> {
            assertThat(t.amount().isPositive()).isTrue();
            assertThat(t.paidBy()).isNotEqualTo(t.paidTo());
            assertThat(t.amount().currency()).isEqualTo(PLN);
        });
    }

    private static long nonZero(Map<UUID, Money> balances) {
        return balances.values().stream().filter(m -> m.minor() != 0).count();
    }

    private static long[] randomZeroSumBalances(Random random, int size) {
        long[] values = new long[size];
        long sum = 0;
        for (int i = 0; i < size - 1; i++) {
            values[i] = random.nextInt(101) - 50;
            sum += values[i];
        }
        values[size - 1] = -sum;
        return values;
    }

    private static long[] randomBalancesBuiltFromSmallGroups(Random random, int targetSize) {
        List<Long> values = new ArrayList<>();
        while (values.size() < targetSize) {
            int groupSize = 2 + random.nextInt(3);
            long sum = 0;
            for (int i = 0; i < groupSize - 1; i++) {
                long value = random.nextInt(41) - 20;
                values.add(value);
                sum += value;
            }
            values.add(-sum);
        }
        Collections.shuffle(values, random);
        return values.stream().mapToLong(Long::longValue).toArray();
    }

    private static int maxZeroSumGroups(long[] values, int remaining) {
        if (remaining == 0) {
            return 0;
        }
        int lowest = Integer.lowestOneBit(remaining);
        int rest = remaining ^ lowest;
        int best = 0;
        for (int sub = rest; ; sub = (sub - 1) & rest) {
            int group = sub | lowest;
            if (sumOf(values, group) == 0) {
                best = Math.max(best, 1 + maxZeroSumGroups(values, remaining ^ group));
            }
            if (sub == 0) {
                break;
            }
        }
        return best;
    }

    private static long sumOf(long[] values, int mask) {
        long sum = 0;
        for (int i = 0; i < values.length; i++) {
            if ((mask & (1 << i)) != 0) {
                sum += values[i];
            }
        }
        return sum;
    }

    private static int bruteForceMinimum(long[] values) {
        long[] nonZero = java.util.Arrays.stream(values).filter(v -> v != 0).toArray();
        int all = (1 << nonZero.length) - 1;
        return nonZero.length - maxZeroSumGroups(nonZero, all);
    }

    @Test
    void nothingToDoForNoBalancesOrAllZeroBalances() {
        assertThat(DebtSimplifier.simplify(Map.of())).isEmpty();
        assertThat(DebtSimplifier.simplify(balances(0, 0, 0))).isEmpty();
    }

    @Test
    void oneDebtIsOneTransfer() {
        List<SuggestedTransfer> transfers = DebtSimplifier.simplify(balances(1000, -1000));

        assertThat(transfers).containsExactly(new SuggestedTransfer(person(2), person(1), pln(1000)));
    }

    @Test
    void oneCreditorOwedByEveryoneElseNeedsOneTransferPerDebtor() {
        Map<UUID, Money> balances = balances(3000, -1000, -1000, -1000);

        List<SuggestedTransfer> transfers = DebtSimplifier.simplify(balances);

        assertThat(transfers).hasSize(3);
        assertThat(transfers).extracting(SuggestedTransfer::paidTo).containsOnly(person(1));
        assertSettlesEverything(balances, transfers);
    }

    @Test
    void peopleWithZeroBalanceAreLeftOut() {
        List<SuggestedTransfer> transfers = DebtSimplifier.simplify(balances(500, 0, -500));

        assertThat(transfers).containsExactly(new SuggestedTransfer(person(3), person(1), pln(500)));
    }

    @Test
    void findsTheMinimumWhereTheObviousGreedyApproachNeedsOneMore() {
        Map<UUID, Money> balances = balances(700, 600, -600, -400, -300);

        List<SuggestedTransfer> transfers = DebtSimplifier.simplify(balances);

        assertThat(transfers).hasSize(3);
        assertSettlesEverything(balances, transfers);
    }

    @Test
    void independentDebtCirclesAreSettledSeparately() {
        Map<UUID, Money> balances = balances(500, -500, 300, -300, 200, -200);

        List<SuggestedTransfer> transfers = DebtSimplifier.simplify(balances);

        assertThat(transfers).hasSize(3);
        assertSettlesEverything(balances, transfers);
    }

    @Test
    void neverNeedsMoreTransfersThanPeopleMinusOne() {
        Random random = new Random(5);
        for (int run = 0; run < 500; run++) {
            Map<UUID, Money> balances = balances(randomZeroSumBalances(random, 2 + random.nextInt(14)));

            List<SuggestedTransfer> transfers = DebtSimplifier.simplify(balances);

            assertSettlesEverything(balances, transfers);
            assertThat((long) transfers.size()).isLessThanOrEqualTo(Math.max(0, nonZero(balances) - 1));
        }
    }

    @Test
    void matchesAnIndependentBruteForceMinimum() {
        Random random = new Random(17);
        for (int run = 0; run < 400; run++) {
            long[] values = random.nextBoolean()
                    ? randomZeroSumBalances(random, 2 + random.nextInt(8))
                    : randomBalancesBuiltFromSmallGroups(random, 3 + random.nextInt(7));
            Map<UUID, Money> balances = balances(values);

            List<SuggestedTransfer> transfers = DebtSimplifier.simplify(balances);

            assertSettlesEverything(balances, transfers);
            assertThat(transfers).as("balances %s", java.util.Arrays.toString(values))
                    .hasSize(bruteForceMinimum(values));
        }
    }

    @Test
    void stillSettlesEverythingForBothSidesOfTheExactSearchLimit() {
        Random random = new Random(99);
        for (int size : new int[]{15, 16, 17, 18, 60}) {
            Map<UUID, Money> balances = balances(randomZeroSumBalances(random, size));

            List<SuggestedTransfer> transfers = DebtSimplifier.simplify(balances);

            assertSettlesEverything(balances, transfers);
            assertThat((long) transfers.size()).isLessThanOrEqualTo(Math.max(0, nonZero(balances) - 1));
        }
    }

    @Test
    void theResultDoesNotDependOnTheOrderOfTheInput() {
        Map<UUID, Money> ordered = balances(700, 600, -600, -400, -300);
        List<Map.Entry<UUID, Money>> entries = new ArrayList<>(ordered.entrySet());
        Collections.shuffle(entries, new Random(3));
        Map<UUID, Money> shuffled = new LinkedHashMap<>();
        entries.forEach(e -> shuffled.put(e.getKey(), e.getValue()));

        assertThat(DebtSimplifier.simplify(shuffled)).isEqualTo(DebtSimplifier.simplify(ordered));
    }

    @Test
    void rejectsBalancesThatDoNotAddUpToZero() {
        assertThatThrownBy(() -> DebtSimplifier.simplify(balances(1000, -900)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("add up to zero");
    }

    @Test
    void rejectsMixedCurrencies() {
        Map<UUID, Money> mixed = new LinkedHashMap<>();
        mixed.put(person(1), pln(100));
        mixed.put(person(2), Money.ofMinor(-100, Currency.getInstance("EUR")));

        assertThatThrownBy(() -> DebtSimplifier.simplify(mixed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one currency");
    }
}
