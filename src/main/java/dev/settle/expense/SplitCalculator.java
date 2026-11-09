package dev.settle.expense;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class SplitCalculator {

    public static final int FULL_BASIS_POINTS = 10_000;

    private SplitCalculator() {
    }

    public static SplitResult splitEqually(Money total, List<UUID> participants, long rotationCursor) {
        requirePositive(total);
        requireValidParticipants(participants);

        int n = participants.size();
        long base = total.minor() / n;
        int remainder = (int) (total.minor() % n);
        int start = Math.floorMod(rotationCursor, n);

        Map<UUID, Money> shares = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            long extra = rotatedPosition(i, start, n) < remainder ? 1 : 0;
            shares.put(participants.get(i), Money.ofMinor(base + extra, total.currency()));
        }
        return new SplitResult(shares, remainder);
    }

    public static SplitResult splitByPercentage(
            Money total, Map<UUID, Integer> basisPoints, long rotationCursor) {
        requirePositive(total);
        requireValidParticipants(new ArrayList<>(basisPoints.keySet()));

        long sum = 0;
        for (Integer bp : basisPoints.values()) {
            if (bp == null || bp < 0) {
                throw new IllegalArgumentException("Percentages must not be negative");
            }
            sum += bp;
        }
        if (sum != FULL_BASIS_POINTS) {
            throw new IllegalArgumentException("Percentages must add up to 100%, got " + sum / 100.0 + "%");
        }

        List<UUID> ids = new ArrayList<>(basisPoints.keySet());
        int n = ids.size();
        int start = Math.floorMod(rotationCursor, n);

        long[] floors = new long[n];
        long[] fractions = new long[n];
        long allocated = 0;
        for (int i = 0; i < n; i++) {
            long product;
            try {
                product = Math.multiplyExact(total.minor(), (long) basisPoints.get(ids.get(i)));
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException("Amount is too large for a percentage split", e);
            }
            floors[i] = product / FULL_BASIS_POINTS;
            fractions[i] = product % FULL_BASIS_POINTS;
            allocated += floors[i];
        }

        int leftover = (int) (total.minor() - allocated);

        List<Integer> priority = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            priority.add(i);
        }
        priority.sort(Comparator
                .<Integer>comparingLong(i -> -fractions[i])
                .thenComparingInt(i -> rotatedPosition(i, start, n)));

        for (int k = 0; k < leftover; k++) {
            floors[priority.get(k)]++;
        }

        Map<UUID, Money> shares = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            shares.put(ids.get(i), Money.ofMinor(floors[i], total.currency()));
        }
        return new SplitResult(shares, leftover);
    }

    public static SplitResult splitByExactAmounts(Money total, Map<UUID, Money> amounts) {
        requirePositive(total);
        requireValidParticipants(new ArrayList<>(amounts.keySet()));

        Money sum = Money.zero(total.currency());
        for (Money amount : amounts.values()) {
            if (amount == null) {
                throw new IllegalArgumentException("Amount must not be null");
            }
            if (amount.isNegative()) {
                throw new IllegalArgumentException("Amounts must not be negative");
            }
            sum = sum.plus(amount);
        }
        if (!sum.equals(total)) {
            throw new IllegalArgumentException("Amounts add up to " + sum + " but the total is " + total);
        }
        return new SplitResult(new LinkedHashMap<>(amounts), 0);
    }

    private static int rotatedPosition(int index, int start, int n) {
        return Math.floorMod(index - start, n);
    }

    private static void requirePositive(Money total) {
        if (total == null || !total.isPositive()) {
            throw new IllegalArgumentException("Total must be greater than zero");
        }
    }

    private static void requireValidParticipants(List<UUID> participants) {
        if (participants == null || participants.isEmpty()) {
            throw new IllegalArgumentException("At least one participant is required");
        }
        if (participants.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Participant must not be null");
        }
        if (new HashSet<>(participants).size() != participants.size()) {
            throw new IllegalArgumentException("Participants must be unique");
        }
    }
}
