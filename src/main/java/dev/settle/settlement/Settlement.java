package dev.settle.settlement;

import dev.settle.expense.Money;
import dev.settle.group.Group;
import dev.settle.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "settlements")
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paid_by", nullable = false, updatable = false)
    private User paidBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paid_to", nullable = false, updatable = false)
    private User paidTo;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "settled_on", nullable = false, updatable = false)
    private LocalDate settledOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Settlement() {
    }

    public Settlement(Group group, User paidBy, User paidTo, Money amount, LocalDate settledOn) {
        this.group = Objects.requireNonNull(group, "group");
        this.paidBy = Objects.requireNonNull(paidBy, "paidBy");
        this.paidTo = Objects.requireNonNull(paidTo, "paidTo");
        Objects.requireNonNull(amount, "amount");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Settlement amount must be positive");
        }
        this.amountMinor = amount.minor();
        this.currency = amount.currency().getCurrencyCode();
        this.settledOn = Objects.requireNonNull(settledOn, "settledOn");
        this.createdAt = Instant.now();
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

    public User getPaidTo() {
        return paidTo;
    }

    public Money getAmount() {
        return Money.ofMinor(amountMinor, Currency.getInstance(currency));
    }

    public LocalDate getSettledOn() {
        return settledOn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Settlement other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
