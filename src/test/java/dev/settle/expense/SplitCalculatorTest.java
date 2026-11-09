package dev.settle.expense;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SplitCalculatorTest {

    private static final Currency PLN = Currency.getInstance("PLN");
    private static final Currency JPY = Currency.getInstance("JPY");

    private static Money pln(long minor) {
        return Money.ofMinor(minor, PLN);
    }

    private static List<UUID> people(int n) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ids.add(new UUID(0, i + 1));
        }
        return ids;
    }

    private static List<Long> minors(Map<UUID, Money> shares) {
        return shares.values().stream().map(Money::minor).toList();
    }

    private static long sum(Map<UUID, Money> shares) {
        return shares.values().stream().mapToLong(Money::minor).sum();
    }

    private static Map<UUID, Money> equally(Money total, List<UUID> people, long cursor) {
        return SplitCalculator.splitEqually(total, people, cursor).shares();
    }

    private static Map<UUID, Money> byPercentage(Money total, Map<UUID, Integer> bps, long cursor) {
        return SplitCalculator.splitByPercentage(total, bps, cursor).shares();
    }

    private static Map<UUID, Money> byExact(Money total, Map<UUID, Money> amounts) {
        return SplitCalculator.splitByExactAmounts(total, amounts).shares();
    }

    @Nested
    class EqualSplit {

        @Test
        void tenZlotyBetweenThreePeopleGivesTheExtraGroszToTheFirstAtOffsetZero() {
            Map<UUID, Money> shares = equally(pln(1000), people(3), 0);

            assertThat(minors(shares)).containsExactly(334L, 333L, 333L);
        }

        @ParameterizedTest(name = "offset {0} -> {1}, {2}, {3}")
        @CsvSource({
                "0, 334, 333, 333",
                "1, 333, 334, 333",
                "2, 333, 333, 334",
                "3, 334, 333, 333",
                "-1, 333, 333, 334",
        })
        void extraGroszRotatesWithTheOffset(int offset, long first, long second, long third) {
            Map<UUID, Money> shares = equally(pln(1000), people(3), offset);

            assertThat(minors(shares)).containsExactly(first, second, third);
        }

        @Test
        void twoLeftoverGroszGoToTwoConsecutiveParticipants() {
            assertThat(minors(equally(pln(1001), people(3), 0)))
                    .containsExactly(334L, 334L, 333L);
            assertThat(minors(equally(pln(1001), people(3), 1)))
                    .containsExactly(333L, 334L, 334L);
            assertThat(minors(equally(pln(1001), people(3), 2)))
                    .containsExactly(334L, 333L, 334L);
        }

        @Test
        void evenDivisionHasNoLeftoverAndIgnoresTheOffset() {
            assertThat(minors(equally(pln(900), people(3), 7)))
                    .containsExactly(300L, 300L, 300L);
        }

        @Test
        void singleParticipantPaysEverything() {
            assertThat(minors(equally(pln(1234), people(1), 5))).containsExactly(1234L);
        }

        @Test
        void totalSmallerThanNumberOfPeopleStillAddsUp() {
            Map<UUID, Money> shares = equally(pln(2), people(3), 0);

            assertThat(minors(shares)).containsExactly(1L, 1L, 0L);
        }

        @Test
        void worksForCurrenciesWithoutMinorUnits() {
            Map<UUID, Money> shares =
                    equally(Money.ofMinor(100, JPY), people(3), 0);

            assertThat(minors(shares)).containsExactly(34L, 33L, 33L);
            assertThat(shares.values()).allMatch(m -> m.currency().equals(JPY));
        }

        @Test
        void keepsParticipantOrder() {
            List<UUID> people = people(4);

            assertThat(equally(pln(1000), people, 0).keySet()).containsExactlyElementsOf(people);
        }

        @Test
        void extraGroszIsFairlySharedOverAFullRotation() {
            List<UUID> people = people(3);
            Map<UUID, Long> paid = new LinkedHashMap<>();
            people.forEach(id -> paid.put(id, 0L));

            for (int expenseNo = 0; expenseNo < 30; expenseNo++) {
                equally(pln(1000), people, expenseNo)
                        .forEach((id, share) -> paid.merge(id, share.minor(), Long::sum));
            }

            assertThat(paid.values()).containsOnly(10_000L);
        }

        @Test
        void sharesAlwaysAddUpAndDifferByAtMostOneGrosz() {
            Random random = new Random(42);
            for (int run = 0; run < 5_000; run++) {
                long total = 1 + random.nextInt(2_000_000);
                int n = 1 + random.nextInt(15);
                int offset = random.nextInt(200) - 100;

                Map<UUID, Money> shares = equally(pln(total), people(n), offset);

                assertThat(sum(shares)).as("total=%d n=%d offset=%d", total, n, offset).isEqualTo(total);
                long min = Collections.min(minors(shares));
                long max = Collections.max(minors(shares));
                assertThat(max - min).isLessThanOrEqualTo(1);
            }
        }
    }

    @Nested
    class PercentageSplit {

        private Map<UUID, Integer> shares(List<UUID> ids, int... bps) {
            Map<UUID, Integer> map = new LinkedHashMap<>();
            for (int i = 0; i < ids.size(); i++) {
                map.put(ids.get(i), bps[i]);
            }
            return map;
        }

        @Test
        void splitsByPercentage() {
            List<UUID> ids = people(2);

            Map<UUID, Money> result = byPercentage(pln(10_000), shares(ids, 7000, 3000), 0);

            assertThat(minors(result)).containsExactly(7000L, 3000L);
        }

        @Test
        void leftoverGoesToTheShareThatLostTheMostToRounding() {
            List<UUID> ids = people(3);

            Map<UUID, Money> result = byPercentage(pln(1000), shares(ids, 3333, 3333, 3334), 0);

            assertThat(minors(result)).containsExactly(333L, 333L, 334L);
        }

        @Test
        void tiesAreBrokenByTheRotation() {
            List<UUID> ids = people(2);

            assertThat(minors(byPercentage(pln(101), shares(ids, 5000, 5000), 0)))
                    .containsExactly(51L, 50L);
            assertThat(minors(byPercentage(pln(101), shares(ids, 5000, 5000), 1)))
                    .containsExactly(50L, 51L);
        }

        @Test
        void zeroPercentParticipantPaysNothing() {
            List<UUID> ids = people(3);

            Map<UUID, Money> result = byPercentage(pln(1001), shares(ids, 5000, 5000, 0), 0);

            assertThat(result.get(ids.get(2)).minor()).isZero();
            assertThat(sum(result)).isEqualTo(1001);
        }

        @Test
        void rejectsPercentagesThatDoNotAddUpTo100() {
            assertThatThrownBy(() -> byPercentage(pln(1000), shares(people(2), 5000, 4000), 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("100%");
        }

        @Test
        void rejectsNegativePercentages() {
            assertThatThrownBy(() -> byPercentage(pln(1000), shares(people(2), 11000, -1000), 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsAmountsThatWouldOverflow() {
            assertThatThrownBy(() -> byPercentage(
                    pln(Long.MAX_VALUE), shares(people(2), 5000, 5000), 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("too large");
        }

        @Test
        void sharesAlwaysAddUpAndStayWithinOneGroszOfTheExactValue() {
            Random random = new Random(7);
            for (int run = 0; run < 5_000; run++) {
                long total = 1 + random.nextInt(5_000_000);
                int n = 1 + random.nextInt(8);
                List<UUID> ids = people(n);
                int[] bps = randomBasisPoints(random, n);

                Map<UUID, Money> result =
                        byPercentage(pln(total), shares(ids, bps), random.nextInt(50));

                assertThat(sum(result)).as("total=%d bps=%s", total, java.util.Arrays.toString(bps)).isEqualTo(total);
                for (int i = 0; i < n; i++) {
                    double exact = total * bps[i] / 10_000.0;
                    assertThat((double) result.get(ids.get(i)).minor()).isBetween(exact - 1, exact + 1);
                }
            }
        }

        private static int[] randomBasisPoints(Random random, int n) {
            int[] cuts = new int[n + 1];
            cuts[n] = 10_000;
            for (int i = 1; i < n; i++) {
                cuts[i] = random.nextInt(10_001);
            }
            java.util.Arrays.sort(cuts);
            int[] parts = new int[n];
            for (int i = 0; i < n; i++) {
                parts[i] = cuts[i + 1] - cuts[i];
            }
            return parts;
        }
    }

    @Nested
    class ExactAmountsSplit {

        @Test
        void acceptsAmountsThatAddUp() {
            List<UUID> ids = people(2);
            Map<UUID, Money> amounts = Map.of(ids.get(0), pln(3000), ids.get(1), pln(2000));

            Map<UUID, Money> result = byExact(pln(5000), amounts);

            assertThat(result).isEqualTo(amounts);
        }

        @Test
        void rejectsAmountsThatDoNotAddUp() {
            List<UUID> ids = people(2);
            Map<UUID, Money> amounts = Map.of(ids.get(0), pln(3000), ids.get(1), pln(1999));

            assertThatThrownBy(() -> byExact(pln(5000), amounts))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("49.99 PLN")
                    .hasMessageContaining("50.00 PLN");
        }

        @Test
        void rejectsNegativeAmounts() {
            List<UUID> ids = people(2);
            Map<UUID, Money> amounts = Map.of(ids.get(0), pln(6000), ids.get(1), pln(-1000));

            assertThatThrownBy(() -> byExact(pln(5000), amounts))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsAmountsInAnotherCurrency() {
            List<UUID> ids = people(1);
            Map<UUID, Money> amounts = Map.of(ids.get(0), Money.ofMinor(5000, JPY));

            assertThatThrownBy(() -> byExact(pln(5000), amounts))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Currency mismatch");
        }
    }

    @Nested
    class Rotation {

        private List<List<Long>> run(List<Long> amounts, List<UUID> people) {
            long cursor = 0;
            List<List<Long>> history = new ArrayList<>();
            for (long amount : amounts) {
                SplitResult result = SplitCalculator.splitEqually(pln(amount), people, cursor);
                cursor += result.leftoverUnits();
                history.add(minors(result.shares()));
            }
            return history;
        }

        @Test
        void expenseThatDividesEvenlyDoesNotUseUpAnybodysTurn() {
            List<List<Long>> history = run(List.of(1000L, 900L, 1000L), people(3));

            assertThat(history.get(0)).containsExactly(334L, 333L, 333L);
            assertThat(history.get(1)).containsExactly(300L, 300L, 300L);
            assertThat(history.get(2)).containsExactly(333L, 334L, 333L);
        }

        @Test
        void leftoverUnitsAreReported() {
            assertThat(SplitCalculator.splitEqually(pln(1000), people(3), 0).leftoverUnits()).isEqualTo(1);
            assertThat(SplitCalculator.splitEqually(pln(900), people(3), 0).leftoverUnits()).isZero();
            assertThat(SplitCalculator.splitEqually(pln(1001), people(3), 0).leftoverUnits()).isEqualTo(2);
            assertThat(SplitCalculator.splitByExactAmounts(
                    pln(100), Map.of(people(1).getFirst(), pln(100))).leftoverUnits()).isZero();

            Map<UUID, Integer> bps = new LinkedHashMap<>();
            List<UUID> ids = people(3);
            bps.put(ids.get(0), 3333);
            bps.put(ids.get(1), 3333);
            bps.put(ids.get(2), 3334);
            assertThat(SplitCalculator.splitByPercentage(pln(1000), bps, 0).leftoverUnits()).isEqualTo(1);
        }

        @Test
        void cursorAdvancesByTheNumberOfUnitsHandedOutNotByOne() {
            List<List<Long>> history = run(List.of(1001L, 1001L, 1001L), people(3));

            assertThat(history.get(0)).containsExactly(334L, 334L, 333L);
            assertThat(history.get(1)).containsExactly(334L, 333L, 334L);
            assertThat(history.get(2)).containsExactly(333L, 334L, 334L);
            for (int person = 0; person < 3; person++) {
                int p = person;
                assertThat(history.stream().mapToLong(h -> h.get(p)).sum()).isEqualTo(1001L);
            }
        }

        @Test
        void nobodyPaysMoreThanOneGroszMoreThanAnybodyElseOverManyMixedExpenses() {
            Random random = new Random(99);
            for (int run = 0; run < 300; run++) {
                int n = 2 + random.nextInt(7);
                List<UUID> people = people(n);
                long[] paid = new long[n];
                long cursor = 0;

                for (int expense = 0; expense < 60; expense++) {
                    long amount = (random.nextBoolean() ? n : 1) * (1 + random.nextInt(5_000)) + random.nextInt(3);
                    SplitResult result = SplitCalculator.splitEqually(pln(amount), people, cursor);
                    cursor += result.leftoverUnits();
                    List<Long> shares = minors(result.shares());
                    for (int i = 0; i < n; i++) {
                        paid[i] += shares.get(i);
                    }
                }

                long spread = java.util.Arrays.stream(paid).max().orElseThrow()
                        - java.util.Arrays.stream(paid).min().orElseThrow();
                assertThat(spread).as("n=%d", n).isLessThanOrEqualTo(1);
            }
        }
    }

    @Nested
    class CommonValidation {

        @ParameterizedTest
        @CsvSource({"0", "-100"})
        void rejectsNonPositiveTotal(long total) {
            assertThatThrownBy(() -> equally(pln(total), people(2), 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("greater than zero");
        }

        @Test
        void rejectsEmptyParticipants() {
            assertThatThrownBy(() -> equally(pln(100), List.of(), 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("participant");
        }

        @Test
        void rejectsDuplicateParticipants() {
            UUID ann = new UUID(0, 1);

            assertThatThrownBy(() -> equally(pln(100), List.of(ann, ann), 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unique");
        }
    }
}
