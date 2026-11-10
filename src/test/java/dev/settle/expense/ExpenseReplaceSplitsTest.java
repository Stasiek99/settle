package dev.settle.expense;

import dev.settle.group.Group;
import dev.settle.support.TestEntities;
import dev.settle.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpenseReplaceSplitsTest {

    private static final Currency PLN = Currency.getInstance("PLN");

    private User ann;
    private User bob;
    private User cid;
    private Expense expense;

    @BeforeEach
    void createAnExpenseSplitBetweenAnnAndBob() {
        ann = TestEntities.user("ann@example.com", "Ann");
        bob = TestEntities.user("bob@example.com", "Bob");
        cid = TestEntities.user("cid@example.com", "Cid");
        Group group = TestEntities.group("Flat", "PLN", ann);
        expense = new Expense(group, ann, "Taxi", money(1000), SplitType.EQUAL, LocalDate.of(2026, 11, 9), 0);
        expense.addSplit(ann, money(500));
        expense.addSplit(bob, money(500));
    }

    private static Money money(long minor) {
        return Money.ofMinor(minor, PLN);
    }

    private static Map<User, Money> shares(Object... pairs) {
        Map<User, Money> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put((User) pairs[i], money((Long) pairs[i + 1]));
        }
        return result;
    }

    private List<Long> amountsOf(User user) {
        return expense.getSplits().stream()
                .filter(s -> s.getUser().equals(user)).map(ExpenseSplit::getAmountMinor).toList();
    }

    @Test
    void keepsTheRowsOfPeopleWhoStayAndUpdatesTheirAmounts() {
        List<ExpenseSplit> before = List.copyOf(expense.getSplits());

        expense.replaceSplits(shares(ann, 700L, bob, 300L));

        assertThat(expense.getSplits()).containsExactlyElementsOf(before);
        assertThat(amountsOf(ann)).containsExactly(700L);
        assertThat(amountsOf(bob)).containsExactly(300L);
    }

    @Test
    void dropsPeopleWhoAreLeftOut() {
        expense.replaceSplits(shares(ann, 1000L));

        assertThat(expense.getSplits()).hasSize(1);
        assertThat(amountsOf(bob)).isEmpty();
    }

    @Test
    void addsPeopleWhoJoin() {
        expense.replaceSplits(shares(ann, 400L, bob, 300L, cid, 300L));

        assertThat(expense.getSplits()).hasSize(3);
        assertThat(amountsOf(cid)).containsExactly(300L);
    }

    @Test
    void canSwapSomeoneForSomeoneElseInOneGo() {
        expense.replaceSplits(shares(ann, 500L, cid, 500L));

        assertThat(expense.getSplits()).extracting(s -> s.getUser().getEmail())
                .containsExactlyInAnyOrder("ann@example.com", "cid@example.com");
    }

    @Test
    void theSplitsStillAddUpAfterwards() {
        expense.update(ann, "Taxi", money(1200), SplitType.EXACT, LocalDate.of(2026, 11, 10));
        expense.replaceSplits(shares(ann, 900L, bob, 300L));

        expense.requireSplitsAddUp();
        assertThat(expense.getAmount()).isEqualTo(money(1200));
    }

    @Test
    void refusesASplitInAnotherCurrency() {
        Map<User, Money> wrong = new LinkedHashMap<>();
        wrong.put(ann, Money.ofMinor(1000, Currency.getInstance("EUR")));

        assertThatThrownBy(() -> expense.replaceSplits(wrong)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theCurrencyOfAnExpenseCannotChange() {
        assertThatThrownBy(() -> expense.update(ann, "Taxi", Money.ofMinor(1000, Currency.getInstance("EUR")),
                SplitType.EQUAL, LocalDate.of(2026, 11, 9))).isInstanceOf(IllegalArgumentException.class);
    }
}
