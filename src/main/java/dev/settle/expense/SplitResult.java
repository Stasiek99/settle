package dev.settle.expense;

import java.util.Map;
import java.util.UUID;

public record SplitResult(Map<UUID, Money> shares, int leftoverUnits) {
}
