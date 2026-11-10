package dev.settle.expense;

import dev.settle.group.Group;
import dev.settle.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paid_by", nullable = false)
    private User paidBy;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_type", nullable = false, length = 20)
    private SplitType splitType;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "split_cursor", nullable = false, updatable = false)
    private long splitCursor;

    @Version
    private long version;

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    private List<ExpenseSplit> splits = new ArrayList<>();

    protected Expense() {
    }

    public Expense(Group group, User paidBy, String description, Money amount, SplitType splitType,
                   LocalDate expenseDate, long splitCursor) {
        this.group = Objects.requireNonNull(group, "group");
        this.paidBy = Objects.requireNonNull(paidBy, "paidBy");
        this.description = Objects.requireNonNull(description, "description").trim();
        Objects.requireNonNull(amount, "amount");
        this.amountMinor = amount.minor();
        this.currency = amount.currency().getCurrencyCode();
        this.splitType = Objects.requireNonNull(splitType, "splitType");
        this.expenseDate = Objects.requireNonNull(expenseDate, "expenseDate");
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
        this.splitCursor = splitCursor;
    }

    public void update(User paidBy, String description, Money amount, SplitType splitType, LocalDate expenseDate) {
        Objects.requireNonNull(amount, "amount");
        if (!amount.currency().getCurrencyCode().equals(currency)) {
            throw new IllegalArgumentException("The currency of an expense cannot change");
        }
        this.paidBy = Objects.requireNonNull(paidBy, "paidBy");
        this.description = Objects.requireNonNull(description, "description").trim();
        this.amountMinor = amount.minor();
        this.splitType = Objects.requireNonNull(splitType, "splitType");
        this.expenseDate = Objects.requireNonNull(expenseDate, "expenseDate");
        touch();
    }

    private void touch() {
        Instant now = Instant.now();
        this.updatedAt = now.isAfter(updatedAt) ? now : updatedAt.plusNanos(1_000);
    }

    public void replaceSplits(Map<User, Money> shares) {
        Map<UUID, Money> byUserId = new LinkedHashMap<>();
        shares.forEach((user, share) -> byUserId.put(user.getId(), share));

        splits.removeIf(split -> !byUserId.containsKey(split.getUser().getId()));
        for (ExpenseSplit split : splits) {
            split.setAmountMinor(requireSameCurrency(byUserId.remove(split.getUser().getId())).minor());
        }
        shares.forEach((user, share) -> {
            if (byUserId.containsKey(user.getId())) {
                addSplit(user, share);
            }
        });
    }

    private Money requireSameCurrency(Money share) {
        if (!share.currency().getCurrencyCode().equals(currency)) {
            throw new IllegalArgumentException("Split currency differs from the expense currency");
        }
        return share;
    }

    public void addSplit(User user, Money amount) {
        if (!amount.currency().getCurrencyCode().equals(currency)) {
            throw new IllegalArgumentException("Split currency differs from the expense currency");
        }
        splits.add(new ExpenseSplit(this, user, amount.minor()));
    }

    public void requireSplitsAddUp() {
        long sum = splits.stream().mapToLong(ExpenseSplit::getAmountMinor).sum();
        if (sum != amountMinor) {
            throw new IllegalStateException(
                    "Splits add up to " + sum + " but the expense is " + amountMinor + " (minor units)");
        }
    }

    public UUID getId() {
        return id;
    }

    public Group getGroup() {
        return group;
    }

    public User getPaidBy() {
        return paidBy;
    }

    public String getDescription() {
        return description;
    }

    public Money getAmount() {
        return Money.ofMinor(amountMinor, Currency.getInstance(currency));
    }

    public SplitType getSplitType() {
        return splitType;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getSplitCursor() {
        return splitCursor;
    }

    public long getVersion() {
        return version;
    }

    public List<ExpenseSplit> getSplits() {
        return Collections.unmodifiableList(splits);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Expense other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
