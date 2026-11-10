package dev.settle.performance;

import dev.settle.TestcontainersConfiguration;
import dev.settle.expense.Expense;
import dev.settle.expense.ExpenseRepository;
import dev.settle.expense.Money;
import dev.settle.expense.SplitType;
import dev.settle.group.Group;
import dev.settle.group.GroupRepository;
import dev.settle.group.Membership;
import dev.settle.group.MembershipRepository;
import dev.settle.group.MembershipRole;
import dev.settle.settlement.Settlement;
import dev.settle.settlement.SettlementRepository;
import dev.settle.support.TestAuth;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class QueryCountTest {

    private static final Currency PLN = Currency.getInstance("PLN");
    private static final int SMALL = 2;
    private static final int LARGE = 12;
    private static final int SMALL_ITEMS = 2;
    private static final int LARGE_ITEMS = 30;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EntityManager em;

    @Autowired
    private UserRepository users;

    @Autowired
    private GroupRepository groups;

    @Autowired
    private MembershipRepository memberships;

    @Autowired
    private ExpenseRepository expenses;

    @Autowired
    private SettlementRepository settlements;

    private Statistics statistics;
    private User caller;
    private Group smallGroup;
    private Group largeGroup;

    @BeforeEach
    void seed() {
        statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        caller = users.save(new User("caller@example.com", "Caller"));
        smallGroup = seedGroup("small", SMALL, SMALL_ITEMS);
        largeGroup = seedGroup("large", LARGE, LARGE_ITEMS);
        TestAuth.authenticateAs(caller.getId());
    }

    private Group seedGroup(String name, int memberCount, int itemCount) {
        List<User> members = new ArrayList<>();
        members.add(caller);
        for (int i = 1; i < memberCount; i++) {
            members.add(users.save(new User(name + i + "@example.com", name + " member " + i)));
        }
        Group group = groups.save(new Group(name, "PLN", caller));
        memberships.save(new Membership(group, caller, MembershipRole.OWNER));
        members.stream().skip(1).forEach(m -> memberships.save(new Membership(group, m, MembershipRole.MEMBER)));

        for (int i = 0; i < itemCount; i++) {
            User payer = members.get(i % members.size());
            Expense expense = new Expense(group, payer, "expense " + i,
                    Money.ofMinor(100L * members.size(), PLN), SplitType.EQUAL, LocalDate.now(), 0);
            members.forEach(m -> expense.addSplit(m, Money.ofMinor(100, PLN)));
            expenses.save(expense);

            User from = members.get(i % members.size());
            User to = members.get((i + 1) % members.size());
            settlements.save(new Settlement(group, from, to, Money.ofMinor(50, PLN), LocalDate.now()));
        }
        em.flush();
        return group;
    }

    private long statementsFor(String path, Object... variables) throws Exception {
        em.flush();
        em.clear();
        statistics.clear();
        mvc.perform(get(path, variables)).andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    @ParameterizedTest(name = "GET {0} runs the same number of queries for a small and a large group")
    @ValueSource(strings = {
            "/groups/{id}",
            "/groups/{id}/members",
            "/groups/{id}/expenses?size=100",
            "/groups/{id}/settlements?size=100",
            "/groups/{id}/balances",
            "/groups/{id}/settlements/suggestions"
    })
    void statementCountDoesNotGrowWithTheSizeOfTheGroup(String path) throws Exception {
        long small = statementsFor(path, smallGroup.getId());
        long large = statementsFor(path, largeGroup.getId());

        assertThat(large)
                .as("GET %s: %d statements for %d members/%d items, %d for %d members/%d items",
                        path, small, SMALL, SMALL_ITEMS, large, LARGE, LARGE_ITEMS)
                .isEqualTo(small);
    }

    @Test
    void listingGroupsRunsTheSameNumberOfQueriesRegardlessOfHowManyGroupsTheUserHas() throws Exception {
        User few = users.save(new User("few@example.com", "Few"));
        User many = users.save(new User("many@example.com", "Many"));
        for (int i = 0; i < 2; i++) {
            memberships.save(new Membership(groups.save(new Group("few " + i, "PLN", few)), few, MembershipRole.OWNER));
        }
        for (int i = 0; i < 30; i++) {
            memberships.save(new Membership(groups.save(new Group("many " + i, "PLN", many)), many, MembershipRole.OWNER));
        }

        TestAuth.authenticateAs(few.getId());
        long small = statementsFor("/groups?size=100");
        TestAuth.authenticateAs(many.getId());
        long large = statementsFor("/groups?size=100");

        assertThat(large).as("GET /groups: %d statements for 2 groups, %d for 30", small, large).isEqualTo(small);
    }

    @Test
    void thePageOfExpensesLoadsItsSplitsInABoundedNumberOfQueries() throws Exception {
        long statements = statementsFor("/groups/{id}/expenses?size=100", largeGroup.getId());

        assertThat(statements)
                .as("expense page of %d expenses with %d splits each", LARGE_ITEMS, LARGE)
                .isLessThanOrEqualTo(8);
    }

    @Test
    void theSeededDataIsReallyLarge() {
        UUID id = largeGroup.getId();
        assertThat(expenses.findByGroupId(id, org.springframework.data.domain.Pageable.unpaged()).getTotalElements())
                .isEqualTo(LARGE_ITEMS);
    }
}
