package dev.settle.settlement;

import dev.settle.expense.Money;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;

public final class DebtSimplifier {

    private static final int MAX_EXACT_PARTICIPANTS = 16;

    private static final Comparator<SuggestedTransfer> OUTPUT_ORDER = Comparator
            .comparingLong((SuggestedTransfer t) -> t.amount().minor()).reversed()
            .thenComparing(SuggestedTransfer::paidBy)
            .thenComparing(SuggestedTransfer::paidTo);

    private DebtSimplifier() {
    }

    private record Entry(UUID userId, long minor) {
    }

    private static final class Remaining {

        private final UUID userId;
        private long amount;

        private Remaining(UUID userId, long amount) {
            this.userId = userId;
            this.amount = amount;
        }
    }

    public static List<SuggestedTransfer> simplify(Map<UUID, Money> balances) {
        Currency currency = requireZeroSumInOneCurrency(balances);
        List<Entry> entries = balances.entrySet().stream()
                .filter(e -> e.getValue().minor() != 0)
                .map(e -> new Entry(e.getKey(), e.getValue().minor()))
                .sorted(Comparator.comparing(Entry::userId))
                .toList();
        if (entries.isEmpty()) {
            return List.of();
        }

        List<List<Entry>> groups = entries.size() <= MAX_EXACT_PARTICIPANTS
                ? splitIntoSmallestZeroSumGroups(entries)
                : List.of(entries);

        List<SuggestedTransfer> transfers = new ArrayList<>();
        for (List<Entry> group : groups) {
            transfers.addAll(settleGreedily(group, currency));
        }
        transfers.sort(OUTPUT_ORDER);
        return List.copyOf(transfers);
    }

    private static Currency requireZeroSumInOneCurrency(Map<UUID, Money> balances) {
        Currency currency = null;
        long sum = 0;
        for (Money balance : balances.values()) {
            if (balance == null) {
                throw new IllegalArgumentException("Balance must not be null");
            }
            if (currency == null) {
                currency = balance.currency();
            } else if (!currency.equals(balance.currency())) {
                throw new IllegalArgumentException("Balances must all be in one currency");
            }
            sum = Math.addExact(sum, balance.minor());
        }
        if (sum != 0) {
            throw new IllegalArgumentException("Balances must add up to zero, got " + sum + " minor units");
        }
        return currency;
    }

    private static List<List<Entry>> splitIntoSmallestZeroSumGroups(List<Entry> entries) {
        int n = entries.size();
        int masks = 1 << n;
        long[] sum = new long[masks];
        int[] groupsUpTo = new int[masks];
        int[] lastAdded = new int[masks];

        for (int mask = 1; mask < masks; mask++) {
            int lowest = Integer.numberOfTrailingZeros(mask);
            sum[mask] = sum[mask & (mask - 1)] + entries.get(lowest).minor();

            int bestCount = -1;
            int bestIndex = -1;
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    int count = groupsUpTo[mask ^ (1 << i)];
                    if (count > bestCount) {
                        bestCount = count;
                        bestIndex = i;
                    }
                }
            }
            groupsUpTo[mask] = bestCount + (sum[mask] == 0 ? 1 : 0);
            lastAdded[mask] = bestIndex;
        }

        List<Integer> order = new ArrayList<>();
        for (int mask = masks - 1; mask != 0; mask ^= 1 << lastAdded[mask]) {
            order.add(lastAdded[mask]);
        }
        Collections.reverse(order);

        List<List<Entry>> groups = new ArrayList<>();
        List<Entry> current = new ArrayList<>();
        long running = 0;
        for (int index : order) {
            Entry entry = entries.get(index);
            current.add(entry);
            running += entry.minor();
            if (running == 0) {
                groups.add(current);
                current = new ArrayList<>();
            }
        }
        return groups;
    }

    private static List<SuggestedTransfer> settleGreedily(List<Entry> group, Currency currency) {
        Comparator<Remaining> largestFirst = Comparator
                .comparingLong((Remaining r) -> r.amount).reversed()
                .thenComparing(r -> r.userId);
        PriorityQueue<Remaining> creditors = new PriorityQueue<>(largestFirst);
        PriorityQueue<Remaining> debtors = new PriorityQueue<>(largestFirst);
        for (Entry entry : group) {
            if (entry.minor() > 0) {
                creditors.add(new Remaining(entry.userId(), entry.minor()));
            } else {
                debtors.add(new Remaining(entry.userId(), -entry.minor()));
            }
        }

        List<SuggestedTransfer> transfers = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Remaining creditor = creditors.poll();
            Remaining debtor = debtors.poll();
            long amount = Math.min(creditor.amount, debtor.amount);
            transfers.add(new SuggestedTransfer(debtor.userId, creditor.userId, Money.ofMinor(amount, currency)));
            creditor.amount -= amount;
            debtor.amount -= amount;
            if (creditor.amount > 0) {
                creditors.add(creditor);
            }
            if (debtor.amount > 0) {
                debtors.add(debtor);
            }
        }
        return transfers;
    }
}
