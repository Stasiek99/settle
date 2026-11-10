package dev.settle.expense;

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

import java.util.UUID;

@Entity
@Table(name = "expense_splits")
public class ExpenseSplit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "expense_id", nullable = false, updatable = false)
    private Expense expense;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    protected ExpenseSplit() {
    }

    ExpenseSplit(Expense expense, User user, long amountMinor) {
        this.expense = expense;
        this.user = user;
        this.amountMinor = amountMinor;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    void setAmountMinor(long amountMinor) {
        this.amountMinor = amountMinor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof ExpenseSplit other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
