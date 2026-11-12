package dev.settle.cache;

import dev.settle.TestcontainersConfiguration;
import dev.settle.expense.CreateExpenseRequest;
import dev.settle.expense.CreateExpenseRequest.Participant;
import dev.settle.expense.ExpenseService;
import dev.settle.expense.SplitType;
import dev.settle.group.AddMemberRequest;
import dev.settle.group.Group;
import dev.settle.group.GroupRepository;
import dev.settle.group.GroupService;
import dev.settle.group.Membership;
import dev.settle.group.MembershipRepository;
import dev.settle.group.MembershipRole;
import dev.settle.settlement.CreateSettlementRequest;
import dev.settle.settlement.SettlementService;
import dev.settle.support.TestAuth;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class CacheBehaviourTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private GroupRepository groups;

    @Autowired
    private MembershipRepository memberships;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private CacheManager caches;

    @Autowired
    private EntityManager em;

    private Statistics statistics;
    private User ann;
    private User bob;
    private Group group;

    @BeforeEach
    void seed() {
        statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        ann = users.save(new User("ann@example.com", "Ann"));
        bob = users.save(new User("bob@example.com", "Bob"));
        group = groups.save(new Group("Flat", "PLN", ann));
        memberships.save(new Membership(group, ann, MembershipRole.OWNER));
        memberships.save(new Membership(group, bob, MembershipRole.MEMBER));
        TestAuth.authenticateAs(ann.getId());
        em.flush();
        em.clear();
    }

    private long statementsFor(String path) throws Exception {
        em.clear();
        statistics.clear();
        mvc.perform(get(path, group.getId())).andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    private void addExpense(long amount) {
        expenseService.create(group.getId(), new CreateExpenseRequest("expense", amount, "PLN", ann.getId(),
                SplitType.EQUAL, null, List.of(new Participant(ann.getId(), null, null),
                new Participant(bob.getId(), null, null))));
        em.flush();
        em.clear();
    }

    @Test
    void aRepeatedBalancesRequestIsServedWithoutTouchingTheDatabase() throws Exception {
        statementsFor("/groups/{id}/balances");

        assertThat(statementsFor("/groups/{id}/balances")).isZero();
    }

    @Test
    void aRepeatedSuggestionsRequestIsServedWithoutTouchingTheDatabase() throws Exception {
        statementsFor("/groups/{id}/settlements/suggestions");

        assertThat(statementsFor("/groups/{id}/settlements/suggestions")).isZero();
    }

    @Test
    void anExpenseInvalidatesTheCachedBalancesAndSuggestions() throws Exception {
        mvc.perform(get("/groups/{id}/balances", group.getId()))
                .andExpect(jsonPath("$.currencies[0].balances[*].balance").value(contains(0, 0)));
        mvc.perform(get("/groups/{id}/settlements/suggestions", group.getId()))
                .andExpect(jsonPath("$.currencies[0].transfers.length()").value(0));

        addExpense(1000);

        mvc.perform(get("/groups/{id}/balances", group.getId()))
                .andExpect(jsonPath("$.currencies[0].balances[0].balance").value(500));
        mvc.perform(get("/groups/{id}/settlements/suggestions", group.getId()))
                .andExpect(jsonPath("$.currencies[0].transfers.length()").value(1));
    }

    @Test
    void aSettlementInvalidatesTheCachedBalancesAndSuggestions() throws Exception {
        addExpense(1000);
        mvc.perform(get("/groups/{id}/settlements/suggestions", group.getId()))
                .andExpect(jsonPath("$.currencies[0].transfers.length()").value(1));
        mvc.perform(get("/groups/{id}/balances", group.getId()))
                .andExpect(jsonPath("$.currencies[0].balances[0].balance").value(500));

        settlementService.create(group.getId(),
                new CreateSettlementRequest(bob.getId(), ann.getId(), 500L, "PLN", null));
        em.flush();
        em.clear();

        mvc.perform(get("/groups/{id}/settlements/suggestions", group.getId()))
                .andExpect(jsonPath("$.currencies[0].transfers.length()").value(0));
        mvc.perform(get("/groups/{id}/balances", group.getId()))
                .andExpect(jsonPath("$.currencies[0].balances[0].balance").value(0));
    }

    @Test
    void aNewMemberAppearsInTheCachedBalances() throws Exception {
        User cid = users.save(new User("cid@example.com", "Cid"));
        em.flush();
        em.clear();
        mvc.perform(get("/groups/{id}/balances", group.getId()))
                .andExpect(jsonPath("$.currencies[0].balances.length()").value(2));

        groupService.addMember(group.getId(), new AddMemberRequest(cid.getId()));
        em.flush();
        em.clear();

        mvc.perform(get("/groups/{id}/balances", group.getId()))
                .andExpect(jsonPath("$.currencies[0].balances.length()").value(3));
    }

    @Test
    void balancesOfDifferentGroupsDoNotShareAnEntry() throws Exception {
        Group other = groups.save(new Group("Trip", "EUR", ann));
        memberships.save(new Membership(other, ann, MembershipRole.OWNER));
        em.flush();
        em.clear();

        mvc.perform(get("/groups/{id}/balances", group.getId()))
                .andExpect(jsonPath("$.defaultCurrency").value("PLN"));
        mvc.perform(get("/groups/{id}/balances", other.getId()))
                .andExpect(jsonPath("$.defaultCurrency").value("EUR"));
    }

    @Test
    void theMembershipCheckIsServedFromTheCacheOnceKnown() throws Exception {
        mvc.perform(get("/groups/{id}/members", group.getId())).andExpect(status().isOk());

        em.clear();
        statistics.clear();
        mvc.perform(get("/groups/{id}/members", group.getId())).andExpect(status().isOk());
        long second = statistics.getPrepareStatementCount();

        assertThat(caches.getCache(CacheNames.MEMBERSHIPS).get(group.getId() + ":" + ann.getId()))
                .isNotNull();
        assertThat(second).isLessThanOrEqualTo(2);
    }

    @Test
    void aNonMemberIsNeverCachedAsOneAndGetsInOnceAdded() throws Exception {
        User cid = users.save(new User("cid@example.com", "Cid"));
        em.flush();
        em.clear();
        TestAuth.authenticateAs(cid.getId());

        mvc.perform(get("/groups/{id}/members", group.getId())).andExpect(status().isNotFound());
        assertThat(caches.getCache(CacheNames.MEMBERSHIPS).get(group.getId() + ":" + cid.getId())).isNull();

        memberships.save(new Membership(groups.getReferenceById(group.getId()), cid, MembershipRole.MEMBER));
        em.flush();
        em.clear();

        mvc.perform(get("/groups/{id}/members", group.getId())).andExpect(status().isOk());
    }

    @Test
    void anUnknownGroupIsNotCached() throws Exception {
        UUID unknown = UUID.randomUUID();

        mvc.perform(get("/groups/{id}/balances", unknown)).andExpect(status().isNotFound());

        assertThat(caches.getCache(CacheNames.BALANCES).get(unknown)).isNull();
    }
}
